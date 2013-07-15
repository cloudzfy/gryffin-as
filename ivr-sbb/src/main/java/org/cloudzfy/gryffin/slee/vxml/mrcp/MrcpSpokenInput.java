/*
 * gryffin-as, IVR Media Resource Controller in JAIN SLEE
 * Copyright (C) 2013, Cloudzfy
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.cloudzfy.gryffin.slee.vxml.mrcp;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.log4j.Logger;
import org.cloudzfy.slee.resource.mrcp.MrcpChannelActivity;
import org.jvoicexml.ConnectionInformation;
import org.jvoicexml.DtmfRecognizerProperties;
import org.jvoicexml.GrammarDocument;
import org.jvoicexml.SpeechRecognizerProperties;
import org.jvoicexml.documentserver.ExternalGrammarDocument;
import org.jvoicexml.event.error.BadFetchError;
import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.event.error.UnsupportedFormatError;
import org.jvoicexml.event.error.UnsupportedLanguageError;
import org.jvoicexml.implementation.DocumentGrammarImplementation;
import org.jvoicexml.implementation.GrammarImplementation;
import org.jvoicexml.implementation.SpokenInput;
import org.jvoicexml.implementation.SpokenInputEvent;
import org.jvoicexml.implementation.SpokenInputListener;
import org.jvoicexml.implementation.SrgsXmlGrammarImplementation;
import org.jvoicexml.xml.srgs.GrammarType;
import org.jvoicexml.xml.srgs.SrgsXmlDocument;
import org.jvoicexml.xml.vxml.BargeInType;
import org.mrcp4j.MrcpMethodName;
import org.mrcp4j.message.header.MrcpHeaderName;
import org.mrcp4j.message.request.MrcpRequest;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl;

public class MrcpSpokenInput implements SpokenInput {

	private static Logger logger = Logger.getLogger(MrcpSpokenInput.class);
	
	private static final int READER_BUFFER_SIZE = 1024;
	private Object activatedGrammar;
	private int numActiveGrammars;
	private Collection<SpokenInputListener> listeners;
	private MrcpChannelActivity channel;
	
	public MrcpSpokenInput() {
		listeners = new ArrayList<SpokenInputListener>();
	}
	
	@Override
	public void activate() throws NoresourceError {
		logger.info("activating input...");
	}

	@Override
	public void close() {
		logger.info("Closing Spoken Input...");
	}

	@Override
	public String getType() {
		return "mrcp";
	}

	@Override
	public boolean isBusy() {
		return false;
	}

	@Override
	public void open() throws NoresourceError {
//		if(channel == null) {
//			throw new NoresourceError();
//		}
	}

	@Override
	public void passivate() throws NoresourceError {
		logger.info("Passivating input...");
	}

	@Override
	public void connect(ConnectionInformation client) throws IOException {
		MrcpConnectionInformation connectionInfo = (MrcpConnectionInformation) client;
		logger.info("Connecting to " + connectionInfo + " ...");
		if(connectionInfo.getRecogChannel() != null) {
			channel = connectionInfo.getRecogChannel();
		} else {
			throw new IOException("No Recognition Channel.");
		}
	}

	@Override
	public void disconnect(ConnectionInformation client) {
		if(client instanceof MrcpConnectionInformation) {
			channel = null;
			logger.info("Disconnected Spoken Input from " + (MrcpConnectionInformation) client + ".");
		}
	}

	@Override
	public void startRecognition(SpeechRecognizerProperties speech,
			DtmfRecognizerProperties dtmf) throws NoresourceError,
			BadFetchError {
		logger.info("Start recognition...");
		if(activatedGrammar == null || numActiveGrammars == 0) {
			logger.info("No active grammars.");
			throw new NoresourceError();
		}
		try {
			long noInputTimeout = 0;
			boolean hotword = false;
			String reader = null;
			if(activatedGrammar instanceof GrammarDocument) {
				reader = ((GrammarDocument) activatedGrammar).getDocument();
			} else if(activatedGrammar instanceof SrgsXmlDocument) {
				ByteArrayOutputStream buffer = new ByteArrayOutputStream();
				Result result = new StreamResult(buffer);
				ClassLoader loader = TransformerFactoryImpl.class.getClassLoader();
				TransformerFactory transformerFactory = TransformerFactory.newInstance("com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl", loader);
				Transformer transformer = transformerFactory.newTransformer();
				transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
				Source source = new DOMSource((Node) activatedGrammar);
				transformer.transform(source, result);
				reader = buffer.toString();
			}
			MrcpRequest request = constructRecogRequest(
					new StringReader(reader), hotword, noInputTimeout);
			channel.sendRequest(request);
		} catch(Exception e) {
			
		}
		SpokenInputEvent event = new SpokenInputEvent(this, SpokenInputEvent.RECOGNITION_STARTED);
		fireInputEvent(event);
	}
	
	private MrcpRequest constructRecogRequest(Reader reader, boolean hotword, long noInputTimeout) {
		BufferedReader in = new BufferedReader(reader);
		StringBuilder sb = new StringBuilder();
		String line = null;
		try {
			while((line = in.readLine()) != null) {
				sb.append(line);
				sb.append("\n");
			}
			logger.debug("The grammar text: " + sb.toString());
			MrcpRequest request = channel.createRequest(MrcpMethodName.RECOGNIZE);
			if(noInputTimeout != 0) {
				request.addHeader(MrcpHeaderName.NO_INPUT_TIMEOUT.constructHeader(new Long(noInputTimeout)));
				request.addHeader(MrcpHeaderName.START_INPUT_TIMERS.constructHeader(Boolean.TRUE));
			} else {
				request.addHeader(MrcpHeaderName.START_INPUT_TIMERS.constructHeader(Boolean.FALSE));
			}
			if(hotword) {
				request.addHeader(MrcpHeaderName.RECOGNITION_MODE.constructHeader("hotword"));
			}
			
			request.setContent("application/x-jsgf", "request@form-level.store", sb.toString());
			return request;
		} catch (IOException e) {
			logger.error("Unable to read buffer. " + e);
		}
		return null;
	}
	
	void fireInputEvent(SpokenInputEvent event) {
		synchronized(listeners) {
			Collection<SpokenInputListener> copy = new ArrayList<SpokenInputListener>();
			copy.addAll(listeners);
			for(SpokenInputListener current : copy) {
				current.inputStatusChanged(event);
			}
		}
	}

	@Override
	public void stopRecognition() {
		logger.info("Stopping recognition...");
		MrcpRequest request = channel.createRequest(MrcpMethodName.STOP);
		channel.sendRequest(request);
	}

	@Override
	public void addListener(SpokenInputListener inputListener) {
		synchronized(listeners) {
			listeners.add(inputListener);
		}
	}

	@Override
	public void removeListener(SpokenInputListener inputListner) {
		synchronized(listeners) {
			listeners.remove(inputListner);
		}
	}

	@Override
	public void activateGrammars(Collection<GrammarImplementation<?>> grammars)
			throws BadFetchError, UnsupportedLanguageError,
			UnsupportedFormatError, NoresourceError {
		
		for(GrammarImplementation<? extends Object> current : grammars) {
			if(current instanceof DocumentGrammarImplementation) {
				DocumentGrammarImplementation grammar = (DocumentGrammarImplementation) current;
				activatedGrammar = grammar.getGrammar();
				numActiveGrammars = 1;
			}else if(current instanceof SrgsXmlGrammarImplementation) {
				SrgsXmlGrammarImplementation grammar = (SrgsXmlGrammarImplementation) current;
				activatedGrammar = grammar.getGrammar();
				numActiveGrammars = 1;
			} else {
				throw new UnsupportedFormatError();
			}
		}
	}

	@Override
	public void deactivateGrammars(Collection<GrammarImplementation<?>> grammars)
			throws NoresourceError, BadFetchError {
		for(GrammarImplementation<? extends Object> current : grammars) {
			if(current instanceof DocumentGrammarImplementation) {
				DocumentGrammarImplementation grammar = (DocumentGrammarImplementation) current;
				if(grammar.getGrammar().equals(activatedGrammar)) {
					numActiveGrammars = 0;
				}
			} else if(current instanceof SrgsXmlGrammarImplementation) {
				SrgsXmlGrammarImplementation grammar = (SrgsXmlGrammarImplementation) current;
				if(grammar.getGrammar().equals(activatedGrammar)) {
					numActiveGrammars = 0;
				}
			}
		}
	}

	@Override
	public Collection<BargeInType> getSupportedBargeInTypes() {
		Collection<BargeInType> types = new ArrayList<BargeInType>();
		types.add(BargeInType.SPEECH);
		types.add(BargeInType.HOTWORD);
		return types;
	}

	@Override
	public Collection<GrammarType> getSupportedGrammarTypes() {
		Collection<GrammarType> types = new ArrayList<GrammarType>();
		types.add(GrammarType.JSGF);
		return types;
	}

	@Override
	public URI getUriForNextSpokenInput() throws NoresourceError,
			URISyntaxException {
		return null;
	}

	@Override
	public GrammarImplementation<?> loadGrammar(Reader reader, GrammarType type)
			throws NoresourceError, BadFetchError, UnsupportedFormatError {
		logger.info("Loading grammar from reader...");
		
		char[] buffer = new char[READER_BUFFER_SIZE];
		StringBuilder sb = new StringBuilder();
		int num;
		try {
			do {
				num = reader.read(buffer);
				if(num >= 0) {
					sb.append(buffer, 0, num);
				}
			} while(num >= 0);
		} catch(IOException e) {
			throw new BadFetchError(e);
		}
		String encoding = System.getProperty("file.encoding");
		if(type.equals(GrammarType.JSGF)) {
			GrammarDocument document = new ExternalGrammarDocument(null, sb.toString().getBytes(), encoding, true);
			document.setMediaType(type);
			return new DocumentGrammarImplementation(document);
		} else if(type.equals(GrammarType.SRGS_ABNF)) {
			InputStream stream = new ByteArrayInputStream(sb.toString().getBytes());
			try {
				SrgsXmlDocument document = new SrgsXmlDocument(new InputSource(stream));
				return new SrgsXmlGrammarImplementation(document);
			} catch (ParserConfigurationException e) {
				logger.error("Unable to parse Input Stream to Input Source. ", e);
			} catch (SAXException e) {
				logger.error("Unable to parse Input Stream to Input Source. ", e);
			} catch (IOException e) {
				logger.error("Unable to parse Input Stream to Input Source. ", e);
			}
		}
		return null;
	}

}

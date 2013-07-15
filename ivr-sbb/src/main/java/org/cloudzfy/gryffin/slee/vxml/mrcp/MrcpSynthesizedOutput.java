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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;

import org.apache.log4j.Logger;
import org.cloudzfy.slee.resource.mrcp.MrcpChannelActivity;
import org.jvoicexml.ConnectionInformation;
import org.jvoicexml.DocumentServer;
import org.jvoicexml.SpeakableSsmlText;
import org.jvoicexml.SpeakableText;
import org.jvoicexml.event.error.BadFetchError;
import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.implementation.ObservableSynthesizedOutput;
import org.jvoicexml.implementation.SynthesizedOutput;
import org.jvoicexml.implementation.SynthesizedOutputListener;
import org.jvoicexml.xml.ssml.SsmlDocument;
import org.mrcp4j.MrcpMethodName;
import org.mrcp4j.message.request.MrcpRequest;
import org.xml.sax.InputSource;

public class MrcpSynthesizedOutput implements SynthesizedOutput, ObservableSynthesizedOutput {

	private static Logger logger = Logger.getLogger(MrcpSynthesizedOutput.class);
	
	private MrcpChannelActivity channel;
	private Collection<SynthesizedOutputListener> listeners;
	
	private int queueCount = 0;
	private Object lock = new Object();
	
	public MrcpSynthesizedOutput() {
		listeners = new ArrayList<SynthesizedOutputListener>();
	}
	
	@Override
	public void activate() throws NoresourceError {
		logger.info("Activating output...");
	}

	@Override
	public void close() {
		logger.info("Closing Synthesized Output...");
	}

	@Override
	public String getType() {
		return "mrcp";
	}

	@Override
	public boolean isBusy() {
		if(queueCount > 0) {
			return true;
		}
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
		logger.info("Passivating output...");
		listeners.clear();
		logger.info("Output passivated.");
	}

	@Override
	public void connect(ConnectionInformation client) throws IOException {
		MrcpConnectionInformation connectionInfo = (MrcpConnectionInformation) client;
		logger.info("Connecting to " + connectionInfo + "...");
		if(connectionInfo.getSynthChannel() != null) {
			channel = connectionInfo.getSynthChannel();
		} else {
			throw new IOException("No Synthesizor Channel.");
		}
	}

	@Override
	public void disconnect(ConnectionInformation client) {
		if(client instanceof MrcpConnectionInformation) {
			channel = null;
			logger.info("Disconnected Synthesized Output from " + (MrcpConnectionInformation)client + ".");
		}
	}

	@Override
	public void cancelOutput() throws NoresourceError {
		logger.info("Cancelling output ...");
		MrcpRequest request = channel.createRequest(MrcpMethodName.BARGE_IN_OCCURRED);
		channel.sendRequest(request);
	}

	@Override
	public boolean supportsBargeIn() {
		return true;
	}

	@Override
	public void addListener(SynthesizedOutputListener listener) {
		synchronized(listeners) {
			listeners.add(listener);
		}
	}

	@Override
	public void removeListener(SynthesizedOutputListener listener) {
		synchronized(listeners) {
			listeners.remove(listener);
		}
	}

	@Override
	public URI getUriForNextSynthesisizedOutput() throws NoresourceError,
			URISyntaxException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void queueSpeakable(SpeakableText speakable, String sessionId,
			DocumentServer documentServer) throws NoresourceError, BadFetchError {
		String speakText = null;
		queueCount++;
		logger.debug("Queue count incremented, now is " + queueCount + ".");
		
		try {
			if(speakable instanceof SpeakableSsmlText) {
				InputStream is = new ByteArrayInputStream(speakable.getSpeakableText().getBytes());
				InputSource src = new InputSource(is);
				SsmlDocument ssml = new SsmlDocument(src);
				speakText = ssml.getSpeak().getTextContent();
			}
			logger.info("Queueing following text: " + speakText);
			play(false, speakText);
		} catch(Exception e) {
			throw new NoresourceError(e.getMessage(), e);
		}
	}

	private void play(boolean isUrl, String prompt) {
		MrcpRequest request = channel.createRequest(MrcpMethodName.SPEAK);
		if(!isUrl) {
			request.setContent("text/plain", null, prompt);
		} else {
			request.setContent("text/uri-list", null, prompt);
		}
		channel.sendRequest(request);
	}
	
	@Override
	public void waitNonBargeInPlayed() {
		synchronized(lock) {
			while(queueCount > 0) {
				try {
					if(Thread.interrupted()) {
						throw new InterruptedException();
					}
					lock.wait();
				} catch(InterruptedException e) {
					logger.warn("Queue count: " + queueCount);
				}
			}
		}
	}

	@Override
	public void waitQueueEmpty() {
		synchronized(lock) {
			while(queueCount > 0) {
				try {
					if(Thread.interrupted()) {
						throw new InterruptedException();
					}
					lock.wait();
				} catch(InterruptedException e) {
					logger.warn("Queue count: " + queueCount);
				}
			}
		}
	}
	
	public void outputEnded() {
		queueCount--;
		synchronized (lock) {
            lock.notifyAll();
        }
	}

}

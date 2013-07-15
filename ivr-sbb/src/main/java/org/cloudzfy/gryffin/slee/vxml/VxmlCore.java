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

package org.cloudzfy.gryffin.slee.vxml;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;

import org.apache.log4j.Logger;
import org.jvoicexml.Configuration;
import org.jvoicexml.ConfigurationException;
import org.jvoicexml.ConnectionInformation;
import org.jvoicexml.DocumentServer;
import org.jvoicexml.JVoiceXmlCore;
import org.jvoicexml.Session;
import org.jvoicexml.documentserver.JVoiceXmlDocumentServer;
import org.jvoicexml.documentserver.SchemeStrategy;
import org.jvoicexml.documentserver.schemestrategy.FileSchemeStrategy;
import org.jvoicexml.documentserver.schemestrategy.HttpSchemeStrategy;
import org.jvoicexml.documentserver.schemestrategy.MappedDocumentStrategy;
import org.jvoicexml.documentserver.schemestrategy.builtin.BooleanGrammarCreator;
import org.jvoicexml.documentserver.schemestrategy.builtin.BuiltinSchemeStrategy;
import org.jvoicexml.documentserver.schemestrategy.builtin.DigitsGrammarCreator;
import org.jvoicexml.documentserver.schemestrategy.builtin.GrammarCreator;
import org.jvoicexml.documentserver.schemestrategy.scriptableobjectserializer.KeyValueSerializer;
import org.jvoicexml.event.ErrorEvent;
import org.jvoicexml.interpreter.GrammarProcessor;
import org.jvoicexml.interpreter.grammar.JVoiceXmlGrammarProcessor;

public class VxmlCore extends Thread implements JVoiceXmlCore {

	private static Logger logger = Logger.getLogger(VxmlCore.class);
	
	private DocumentServer documentServer;
	private GrammarProcessor grammarProcessor;
	
	public VxmlCore() {
		this.documentServer = new JVoiceXmlDocumentServer();
        List<SchemeStrategy> schemeStrategies = new ArrayList<SchemeStrategy>();
        schemeStrategies.add(new MappedDocumentStrategy());
        schemeStrategies.add(new FileSchemeStrategy());
        
        HttpSchemeStrategy httpSchemeStrategy = new HttpSchemeStrategy();
        httpSchemeStrategy.setFetchTimeout(5000);
        httpSchemeStrategy.setSerializer(new KeyValueSerializer());
        schemeStrategies.add(httpSchemeStrategy);
        
        HttpSchemeStrategy httpsSchemeStrategy = new HttpSchemeStrategy();
        httpsSchemeStrategy.setFetchTimeout(5000);
        httpsSchemeStrategy.setScheme("https");
        httpsSchemeStrategy.setSerializer(new KeyValueSerializer());
        schemeStrategies.add(httpsSchemeStrategy);
        
        BuiltinSchemeStrategy builtinSchemeStrategy = new BuiltinSchemeStrategy();
        Collection<GrammarCreator> col = new ArrayList<GrammarCreator>();
        col.add(new BooleanGrammarCreator());
        col.add(new DigitsGrammarCreator());
        builtinSchemeStrategy.setGrammarCreators(col);
        schemeStrategies.add(builtinSchemeStrategy);
        
        ((JVoiceXmlDocumentServer)documentServer).setSchemeStrategies(schemeStrategies);
        
		this.grammarProcessor = new JVoiceXmlGrammarProcessor();
		try {
			this.grammarProcessor.init(null);
		} catch (ConfigurationException e) {
			logger.error("Unable to initialize Grammar Processor. ", e);
		}
	}
	@Override
	public Session createSession(ConnectionInformation client) throws ErrorEvent {
		logger.warn("Unimplemented createSession function.");
		return null;
	}

	@Override
	public String getVersion() {
		return "0.7.6.EA";
	}

	@Override
	public void shutdown() {
		logger.warn("Unimplemented shutdown function.");
	}

	@Override
	public Configuration getConfiguration() {
		final ServiceLoader<Configuration> services = ServiceLoader.load(Configuration.class);
		for(Configuration config : services) {
			return config;
		}
		
		return null;
	}

	@Override
	public DocumentServer getDocumentServer() {
		return this.documentServer;
	}

	@Override
	public GrammarProcessor getGrammarProcessor() {
		return this.grammarProcessor;
	}

}

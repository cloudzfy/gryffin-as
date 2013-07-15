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

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.log4j.Logger;
import org.cloudzfy.gryffin.slee.vxml.mrcp.MrcpConnectionInformation;
import org.cloudzfy.gryffin.slee.vxml.mrcp.MrcpSpokenInputFactory;
import org.cloudzfy.gryffin.slee.vxml.mrcp.MrcpSynthesizedOutputFactory;
import org.cloudzfy.gryffin.slee.vxml.mrcp.MrcpTelephonyFactory;
import org.cloudzfy.slee.resource.mrcp.MrcpChannelActivity;
import org.jvoicexml.ConfigurationException;
import org.jvoicexml.ImplementationPlatform;
import org.jvoicexml.JVoiceXmlCore;
import org.jvoicexml.Session;
import org.jvoicexml.event.ErrorEvent;
import org.jvoicexml.implementation.SpokenInput;
import org.jvoicexml.implementation.SynthesizedOutput;
import org.jvoicexml.implementation.Telephony;
import org.jvoicexml.implementation.jvxml.JVoiceXmlImplementationPlatform;
import org.jvoicexml.implementation.pool.KeyedResourcePool;
import org.jvoicexml.interpreter.JVoiceXmlSession;

public class VxmlUtil {
	
	private Logger logger = Logger.getLogger(VxmlUtil.class);

	private MrcpChannelActivity synthChannel;
	private MrcpChannelActivity recogChannel;
	private ImplementationPlatform platform;
	
	private Session vxmlSession;
	
	public static VxmlUtil getInstance() {
		return new VxmlUtil();
	}
	
	public void setSynthChannel(MrcpChannelActivity synthChannel) {
		this.synthChannel = synthChannel;
	}
	
	public void setRecogChannel(MrcpChannelActivity recogChannel) {
		this.recogChannel = recogChannel;
	}
	
	public ImplementationPlatform getImplementationPlatform() {
		return platform;
	}
	
	public void startup(String uri) {
		MrcpConnectionInformation connection = MrcpConnectionInformation.getInstance();
		connection.setSynthChannel(synthChannel);
		connection.setRecogChannel(recogChannel);
		
		KeyedResourcePool<Telephony> telephonyPool = new KeyedResourcePool<Telephony>();
		KeyedResourcePool<SpokenInput> spokenPool = new KeyedResourcePool<SpokenInput>();
		KeyedResourcePool<SynthesizedOutput> synthesizedPool = new KeyedResourcePool<SynthesizedOutput>();
		try {
			telephonyPool.addResourceFactory(new MrcpTelephonyFactory());
			spokenPool.addResourceFactory(new MrcpSpokenInputFactory());
			synthesizedPool.addResourceFactory(new MrcpSynthesizedOutputFactory());
		} catch (Exception e) {
			logger.error("Unable to add Resource Factory. ", e);
		}
		
		platform = new JVoiceXmlImplementationPlatform(telephonyPool, synthesizedPool, spokenPool, connection);
		try {
			((JVoiceXmlImplementationPlatform)platform).init(null);
		} catch (ConfigurationException e) {
			logger.error("Unable to initialize Implementation Platform. ", e);
		}
		JVoiceXmlCore core = new VxmlCore();
		vxmlSession = new JVoiceXmlSession(platform, core, connection);
		platform.setSession(vxmlSession);
		try {
			logger.info("Get VoiceXML document from: " + uri);
			vxmlSession.call(new URI(uri));
			//vxmlSession.waitSessionEnd();
			//vxmlSession.hangup();
		} catch (URISyntaxException e) {
			logger.info("Unable to parse specific URL.", e);
		} catch (ErrorEvent e) {
			logger.info("Unable to get destination document. ", e);
		}
		
	}
	
}
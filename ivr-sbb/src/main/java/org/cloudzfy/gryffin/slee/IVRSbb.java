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

package org.cloudzfy.gryffin.slee;

import java.net.InetAddress;
import java.text.ParseException;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.ActivityContextInterface;
import javax.slee.RolledBackContext;
import javax.slee.Sbb;
import javax.slee.SbbContext;
import javax.slee.facilities.Tracer;

import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SleeSipProvider;

import org.cloudzfy.gryffin.slee.vxml.VxmlUtil;
import org.cloudzfy.gryffin.slee.vxml.mrcp.MrcpRecognitionResult;
import org.cloudzfy.gryffin.slee.vxml.mrcp.MrcpSynthesizedOutput;
import org.cloudzfy.slee.resource.mrcp.MrcpActivityContextInterfaceFactory;
import org.cloudzfy.slee.resource.mrcp.MrcpChannelActivity;
import org.cloudzfy.slee.resource.mrcp.MrcpProvider;
import org.cloudzfy.slee.resource.mrcp.event.MrcpRequestEvent;
import org.jvoicexml.ImplementationPlatform;
import org.jvoicexml.RecognitionResult;
import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.event.plain.ConnectionDisconnectHangupEvent;
import org.jvoicexml.implementation.MarkerReachedEvent;
import org.jvoicexml.implementation.SpokenInputEvent;
import org.jvoicexml.implementation.jvxml.JVoiceXmlImplementationPlatform;
import org.jvoicexml.implementation.jvxml.JVoiceXmlSystemOutput;
import org.jvoicexml.xml.srgs.ModeType;
import org.mrcp4j.MrcpResourceType;

public abstract class IVRSbb implements Sbb, IVR {
	
	private final static String JBOSS_BIND_ADDRESS = System.getProperty("jboss.bind.address", "192.168.1.100");
	
	private String uri = "http://" + JBOSS_BIND_ADDRESS + ":8080/gryffin-as-vxml/voicexml/hello.vxml";
	private SbbContext sbbContext;
	private Tracer tracer;
	
	private MrcpProvider mrcpProvider;
	private MrcpActivityContextInterfaceFactory mrcpAcif;
	
	private SleeSipProvider sipProvider;
	private MessageFactory messageFactory;
	
	private VxmlUtil vxmlUtil;
	
	public void onCallAck(RequestEvent event, ActivityContextInterface aci) {
		MrcpChannelActivity synthChannel = getMrcpChannelActivity(MrcpResourceType.SPEECHSYNTH);
		MrcpChannelActivity recogChannel = getMrcpChannelActivity(MrcpResourceType.SPEECHRECOG);
		if(synthChannel.getMrcpChannel() == null || recogChannel.getMrcpChannel() == null) {
			tracer.severe("No Media was found in destination endpoint.");
		} else {
			vxmlUtil.setSynthChannel(synthChannel);
			vxmlUtil.setRecogChannel(recogChannel);
			vxmlUtil.startup(uri);
		}
	}
	
	// Speech Synthesizer -----------------------------------------------------
	
	public void onSpeechMarker(MrcpRequestEvent event, ActivityContextInterface aci) {
		ImplementationPlatform platform = vxmlUtil.getImplementationPlatform();
		MarkerReachedEvent markerReachedEvent = new MarkerReachedEvent(null, null, event.getContent());
		((JVoiceXmlImplementationPlatform)platform).outputStatusChanged(markerReachedEvent);
	}
	
	public void onSpeakComplete(MrcpRequestEvent event, ActivityContextInterface aci) {
		ImplementationPlatform platform = vxmlUtil.getImplementationPlatform();
		try {
			MrcpSynthesizedOutput output = (MrcpSynthesizedOutput) ((JVoiceXmlSystemOutput)platform.getSystemOutput()).getSynthesizedOutput();
			output.outputEnded();
		} catch (NoresourceError ne) {
			tracer.warning("No System Output. ");
			DialogActivity dialog = (DialogActivity) getServerTransaction().getDialog();
			try {
				Request request = dialog.createRequest(Request.BYE);
				dialog.sendRequest(request);
			} catch (SipException e) {
				tracer.severe("Unable to create BYE message. ", e);
			}
		} catch (ConnectionDisconnectHangupEvent e) {
			tracer.warning("No System Output. ");
		}
	}

	// Speech Recognizer ------------------------------------------------------
	
	public void onStartofInput(MrcpRequestEvent event, ActivityContextInterface aci) {
		ImplementationPlatform platform = vxmlUtil.getImplementationPlatform();
		SpokenInputEvent spokenInputEvent = new SpokenInputEvent(null, SpokenInputEvent.INPUT_STARTED, ModeType.VOICE);
		((JVoiceXmlImplementationPlatform)platform).inputStatusChanged(spokenInputEvent);
	}

	public void onRecognitionComplete(MrcpRequestEvent event, ActivityContextInterface aci) {
		ImplementationPlatform platform = vxmlUtil.getImplementationPlatform();
		RecognitionResult recognitionResult = new MrcpRecognitionResult(event.getContent());
		SpokenInputEvent spokenInputEvent = null;
		if(recognitionResult.isAccepted()) {
			spokenInputEvent = new SpokenInputEvent(null, SpokenInputEvent.RESULT_ACCEPTED, recognitionResult);
		} else {
			spokenInputEvent = new SpokenInputEvent(null, SpokenInputEvent.RESULT_REJECTED, recognitionResult);
		}
		
		((JVoiceXmlImplementationPlatform)platform).inputStatusChanged(spokenInputEvent);
	}
	
	public void onCallTerminated(RequestEvent event, ActivityContextInterface aci) {
		ImplementationPlatform platform = vxmlUtil.getImplementationPlatform();
		platform.close();
		DialogActivity dialog = getClientDialogActivity();
		try {
			Request request = dialog.createRequest(Request.BYE);
			dialog.sendRequest(request);
		} catch (SipException e) {
			tracer.severe("Unable to create BYE message. ", e);
		}
		ServerTransaction serverTransaction = event.getServerTransaction();
		try {
			Response response = messageFactory.createResponse(Response.OK, event.getRequest());
			serverTransaction.sendResponse(response);
		} catch (ParseException e) {
			tracer.severe("Unable to create 200 OK response. ", e);
		} catch (SipException e) {
			tracer.severe("Unable to sent 200 OK response. ", e);
		} catch (InvalidArgumentException e) {
			tracer.severe("Unable to sent 200 OK response. ", e);
		}
		
	}
	
	private MrcpChannelActivity getMrcpChannelActivity(MrcpResourceType resourceType) {
		ActivityContextInterface[] activities = sbbContext.getActivities();
		for(ActivityContextInterface activity : activities) {
			if(activity.getActivity() instanceof MrcpChannelActivity
					&& ((MrcpChannelActivity)activity.getActivity()).getChannelIdentifier().getResourceType().equals(resourceType)) {
				return (MrcpChannelActivity) activity.getActivity();
			}
		}
		return null;
	}
	
	@Override
	public void addNewChannelgetNewChannel(String channelId, InetAddress host, int port) {
		MrcpChannelActivity channel = mrcpProvider.getNewChannel(channelId, host, port);
		mrcpAcif.getActivityContextInterface(channel).attach(sbbContext.getSbbLocalObject());
	}
	
	public void sbbCreate() throws javax.slee.CreateException {}
	public void sbbPostCreate() throws javax.slee.CreateException {}
	public void sbbActivate() {}
	public void sbbPassivate() {}
	public void sbbRemove() {}
	public void sbbLoad() {}
	public void sbbStore() {}
	public void sbbExceptionThrown(Exception exception, Object event, ActivityContextInterface activity) {}
	public void sbbRolledBack(RolledBackContext context) {}

	private DialogActivity getClientDialogActivity() {
		ActivityContextInterface[] activities = sbbContext.getActivities();
		for(ActivityContextInterface activity : activities) {
			if(activity.getActivity() instanceof DialogActivity) {
				if(!((DialogActivity) activity.getActivity()).isServer()) {
					return (DialogActivity) activity.getActivity();
				}
			}
		}
		return null;
	}
	
	private ServerTransaction getServerTransaction() {
		ActivityContextInterface[] activities = sbbContext.getActivities();
		for(ActivityContextInterface activity : activities) {
			if(activity.getActivity() instanceof ServerTransaction) {
				return (ServerTransaction) activity.getActivity();
			}
		}
		return null;
	}
	
	public void setSbbContext(SbbContext context) {
		this.sbbContext = context;
		this.tracer = sbbContext.getTracer(IVRSbb.class.getSimpleName());
		
		try {
			Context ctx = (Context) new InitialContext().lookup("java:comp/env");
			
			mrcpProvider = (MrcpProvider) ctx.lookup("slee/resource/mrcp/1.0/provider");
			mrcpAcif = (MrcpActivityContextInterfaceFactory) ctx.lookup("slee/resource/mrcp/1.0/acifactory");
			
			sipProvider = (SleeSipProvider) ctx.lookup("slee/resource/jainsip/1.2/provider");
			messageFactory = sipProvider.getMessageFactory();
			
			vxmlUtil = VxmlUtil.getInstance();
		} catch (NamingException e) {
			tracer.severe("Unable to set SBB Context. ", e);
		}
	}
	
	public void unsetSbbContext() {
		this.sbbContext = null;
	}
	
	protected SbbContext getSbbContext() {
		return sbbContext;
	}

}

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
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sdp.Attribute;
import javax.sdp.MediaDescription;
import javax.sdp.SessionDescription;
import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.ServerTransaction;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.CSeqHeader;
import javax.sip.header.CallIdHeader;
import javax.sip.header.ContactHeader;
import javax.sip.header.ContentTypeHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.MaxForwardsHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.ViaHeader;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import javax.slee.ActivityContextInterface;
import javax.slee.ChildRelation;
import javax.slee.RolledBackContext;
import javax.slee.Sbb;
import javax.slee.SbbContext;
import javax.slee.SbbLocalObject;
import javax.slee.facilities.Tracer;

import net.java.slee.resource.sip.DialogActivity;
import net.java.slee.resource.sip.SipActivityContextInterfaceFactory;
import net.java.slee.resource.sip.SleeSipProvider;

import org.cloudzfy.gryffin.slee.util.SdpMessageUtil;

public abstract class CallSbb implements Sbb {

	private final static String JBOSS_BIND_ADDRESS = System.getProperty("jboss.bind.address", "192.168.1.100");
	private final static int JBOSS_SIP_PORT = 5060;
	private final static String JBOSS_SIP_ADDRESS = "sip:" + JBOSS_BIND_ADDRESS + ":" + JBOSS_SIP_PORT;
	
	private final static String ENDPOINT_BIND_ADDRESS = "192.168.1.101";
	private final static String ENDPOINT_SIP_ADDRESS = "sip:" + ENDPOINT_BIND_ADDRESS + ":8060";
	private final static String SIP_TRANSPORT = "UDP";
	
	private SbbContext sbbContext;
	private Tracer tracer;
	
	private SleeSipProvider sipProvider;
	private SipActivityContextInterfaceFactory sipAcif;
	
	private AddressFactory addressFactory;
	private HeaderFactory headerFactory;
	private MessageFactory messageFactory;
	
	private SdpMessageUtil sdpMessageUtil;
	
	public void onCallInvite(RequestEvent event, ActivityContextInterface aci) {
		try {
			replyToRequestEvent(event, Response.TRYING);
			
			String sdpMessage = sdpMessageUtil.constructResourceMessage(event.getRequest().getRawContent());
			Request request = createInvite(event.getRequest(), sdpMessage);
			
			DialogActivity incomingDialog = (DialogActivity)sipProvider
					.getNewDialog(event.getServerTransaction());
			
			DialogActivity outgoingDialog = (DialogActivity)sipProvider
					.getNewDialog(sipProvider.getNewClientTransaction((Request) request.clone()));
			
			incomingDialog.terminateOnBye(true);
			outgoingDialog.terminateOnBye(true);
			
			sipAcif.getActivityContextInterface(incomingDialog).attach(sbbContext.getSbbLocalObject());
			sipAcif.getActivityContextInterface(outgoingDialog).attach(sbbContext.getSbbLocalObject());

			outgoingDialog.sendRequest(request);
			
			replyToRequestEvent(event, Response.RINGING);
			
		} catch (Exception e) {
			tracer.severe("Unable to process INVITE message. ", e);
			replyToRequestEvent(event, Response.SERVICE_UNAVAILABLE);
		}
		
	}
	
	private Request createInvite(Request request, String sdpMessage) {
		try {
			FromHeader fromHeader = headerFactory.createFromHeader(addressFactory.createAddress(JBOSS_SIP_ADDRESS), null);
			ToHeader toHeader = headerFactory.createToHeader(addressFactory.createAddress(ENDPOINT_SIP_ADDRESS), null);
			SipURI requestUri = (SipURI) addressFactory.createURI(ENDPOINT_SIP_ADDRESS);
			
			ViaHeader viaHeader = headerFactory.createViaHeader(JBOSS_BIND_ADDRESS, sipProvider.getListeningPoint(SIP_TRANSPORT).getPort(), SIP_TRANSPORT, null);
			ArrayList<ViaHeader> viaHeaders = new ArrayList<ViaHeader>();
			viaHeaders.add(viaHeader);
			
			CallIdHeader callIdHeader = sipProvider.getNewCallId();
			CSeqHeader cSeqHeader = headerFactory.createCSeqHeader(1L, Request.INVITE);
			MaxForwardsHeader maxForwardsHeader = headerFactory.createMaxForwardsHeader(70);
			
			Request newRequest = messageFactory.createRequest(requestUri, Request.INVITE, callIdHeader, cSeqHeader, 
					fromHeader, toHeader, viaHeaders, maxForwardsHeader);
			
			ContactHeader contactHeader = headerFactory.createContactHeader(addressFactory.createAddress(JBOSS_SIP_ADDRESS));
			newRequest.addHeader(contactHeader);
			
			RouteHeader routeHeader = headerFactory.createRouteHeader(addressFactory.createAddress(ENDPOINT_SIP_ADDRESS));
			newRequest.setHeader(routeHeader);
			
			ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
			newRequest.setContent(sdpMessage, contentTypeHeader);
			
			return newRequest;
			
		} catch (Exception e) {
			tracer.severe("Unable to create SIP INVITE message. ", e);
		}
		
		return null;
	}
	
	@SuppressWarnings("unchecked")
	public void on2xxResponse(ResponseEvent event, ActivityContextInterface aci) {
		Response response = event.getResponse();
		CSeqHeader cSeqHeader = (CSeqHeader) response.getHeader(CSeqHeader.NAME);
		if(cSeqHeader.getMethod().equals(Request.INVITE)) {
			try {
				SessionDescription sd = sdpMessageUtil.getSdpFactory().createSessionDescription(new String(response.getRawContent()));
				
				List<MediaDescription> synthChanns = sdpMessageUtil.getMrcpSynthesizerChannels(sd);
				String synthChannelId = synthChanns.get(0).getAttribute(SdpMessageUtil.SDP_CHANNEL_ATTR_NAME);
				int synthPort = synthChanns.get(0).getMedia().getMediaPort();
				
				List<MediaDescription> recogChanns = sdpMessageUtil.getMrcpRecognizorChannels(sd);
				String recogChannelId = recogChanns.get(0).getAttribute(SdpMessageUtil.SDP_CHANNEL_ATTR_NAME);
				int recogPort = recogChanns.get(0).getMedia().getMediaPort();
				
				MediaDescription recogChann = recogChanns.get(0);
				List<MediaDescription> rtpChanns = sdpMessageUtil.getAudioChannsForRecogChann(sd, recogChann);
				int remoteRtpPort = -1;
				Vector<String> supportedFormats = null;
				Vector<Attribute> rtpmap = new Vector<Attribute>();
				if(rtpChanns.size() > 0) {
					remoteRtpPort = rtpChanns.get(0).getMedia().getMediaPort();
					supportedFormats = rtpChanns.get(0).getMedia().getMediaFormats(true);
					for(Attribute attr : (Vector<Attribute>) rtpChanns.get(0).getAttributes(false)) {
						if(attr.getName().equals(SdpMessageUtil.SDP_RTP_MAP)) {
							rtpmap.add(attr);
						}
					}
				} else {
					tracer.warning("No Media channel specified in the invite request");
				}
				InetAddress endpointAddr = InetAddress.getByName(ENDPOINT_BIND_ADDRESS);
				
				SessionDescription sdReply = sdpMessageUtil.constructInviteResponseToClient(sd, remoteRtpPort, supportedFormats, rtpmap);
				replyToRequestEventWithSdp(sdReply.toString());
				
				SbbLocalObject child = getIVRSbb().create();
				ActivityContextInterface[] activities = sbbContext.getActivities();
				for(ActivityContextInterface activity : activities) {
					activity.attach(child);
					activity.detach(sbbContext.getSbbLocalObject());
				}
				((IVRSbbLocalObject)child).addNewChannelgetNewChannel(recogChannelId, endpointAddr, recogPort);
				((IVRSbbLocalObject)child).addNewChannelgetNewChannel(synthChannelId, endpointAddr, synthPort);
				
				Request ack = event.getDialog().createAck(cSeqHeader.getSeqNumber());
				event.getDialog().sendAck(ack);
				
			} catch (Exception e) {
				tracer.severe("Unable to create ACK Request. ", e);
			}
			
		}
	}
	
	private void replyToRequestEvent(RequestEvent event, int status) {
		try {
			event.getServerTransaction().sendResponse(messageFactory.createResponse(status, event.getRequest()));
		} catch (Exception e) {
			tracer.severe("Unable to reply to request event. ", e);
		}
	}
	
	private void replyToRequestEventWithSdp(String sdp) {
		ServerTransaction stx = getServerTransaction();
		Request request = stx.getRequest();
		
		try {
			ContentTypeHeader contentTypeHeader = headerFactory.createContentTypeHeader("application", "sdp");
			
			String localAddress = sipProvider.getListeningPoints()[0].getIPAddress();
			int localPort = sipProvider.getListeningPoints()[0].getPort();
			Address contactAddress = addressFactory.createAddress("sip:" + localAddress + ":" + localPort);
			ContactHeader contactHeader = headerFactory.createContactHeader(contactAddress);
			
			try {
				Response response = messageFactory.createResponse(Response.OK, request, contentTypeHeader, sdp.getBytes());
				response.setHeader(contactHeader);
				stx.sendResponse(response);
			} catch (Exception e) {
				tracer.severe("Unable to send 200 OK to client. ", e);
			}
		} catch (ParseException e) {
			tracer.severe("Unable to parse Response message header. ", e);
			
			try {
				Response response = messageFactory.createResponse(Response.SERVER_INTERNAL_ERROR, request);
				stx.sendResponse(response);
			} catch (Exception e1) {
				tracer.severe("Unable to send SERVER_INTERNAL_ERROR response. ", e1);
			}
		}
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
	
	private ServerTransaction getServerTransaction() {
		ActivityContextInterface[] activities = sbbContext.getActivities();
		for(ActivityContextInterface activity : activities) {
			if(activity.getActivity() instanceof ServerTransaction) {
				return (ServerTransaction) activity.getActivity();
			}
		}
		return null;
	}
	
	protected SbbContext getSbbContext() {
		return sbbContext;
	}
	
	public void setSbbContext(SbbContext sbbContext) {
		this.sbbContext = sbbContext;
		this.tracer = sbbContext.getTracer(CallSbb.class.getSimpleName());
		
		try {
			Context ctx = (Context)new InitialContext().lookup("java:comp/env");
			
			sipProvider = (SleeSipProvider) ctx.lookup("slee/resource/jainsip/1.2/provider");
			addressFactory = sipProvider.getAddressFactory();
			headerFactory = sipProvider.getHeaderFactory();
			messageFactory = sipProvider.getMessageFactory();
			sdpMessageUtil = SdpMessageUtil.getInstance();
			
			sipAcif = (SipActivityContextInterfaceFactory) ctx.lookup("slee/resource/jainsip/1.2/acifactory");
			
		} catch (NamingException e) {
			tracer.severe("Unable to set SBB Context. ", e);
		}
		
	}
	
	public void unsetSbbContext() {
		this.sbbContext = null;
		this.tracer = null;
	}

	public abstract ChildRelation getIVRSbb();

}

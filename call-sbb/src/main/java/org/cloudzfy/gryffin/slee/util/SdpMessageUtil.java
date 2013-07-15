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

package org.cloudzfy.gryffin.slee.util;

import gov.nist.javax.sdp.MediaDescriptionImpl;
import gov.nist.javax.sdp.fields.ConnectionField;
import gov.nist.javax.sdp.fields.MediaField;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

import javax.sdp.Attribute;
import javax.sdp.Connection;
import javax.sdp.Media;
import javax.sdp.MediaDescription;
import javax.sdp.SdpException;
import javax.sdp.SdpFactory;
import javax.sdp.SessionDescription;

import org.apache.log4j.Logger;
import org.mrcp4j.MrcpResourceType;

public class SdpMessageUtil {

	private Logger logger = Logger.getLogger(SdpMessageUtil.class);
	
	public static final String SDP_SETUP_ATTR_NAME = "setup";
	public static final String SDP_ACTIVE_SETUP = "active";
	public static final String SDP_PASSIVE_SETUP = "passive";
	public static final String SDP_RESOURCE_ATTR_NAME = "resource";
	public static final String SDP_CHANNEL_ATTR_NAME = "channel";
	public static final String SDP_MRCP_PROTOCOL = "TCP/MRCPv2";
	public static final String SDP_SYNTH_RESOURCE = "speechsynth";
	public static final String SDP_RECOG_RESOURCE = "speechrecog";
	public static final String SDP_CMID_ATTR_NAME = "cmid";
	public static final String SDP_RTP_PROTOCOL = "RTP/AVP";
	public static final String SDP_MID_ATTR_NAME = "mid";
	public static final String SDP_APPLICATION_MEDIA = "application";
	public static final String SDP_CONNECTION_ATTR_NAME = "connection";
	public static final String SDP_NEW_CONNECTION = "new";
	public static final String SDP_AUDIO_MEDIA ="audio";
	public static final String SDP_RTP_MAP = "rtpmap";
	
	public SdpFactory sdpFactory;
	
	private SdpMessageUtil() {
		sdpFactory = SdpFactory.getInstance();
	}
	
	public static SdpMessageUtil getInstance() {
		return new SdpMessageUtil();
	}
	
	public SdpFactory getSdpFactory() {
		return sdpFactory;
	}
	
	public List<MediaDescription> getMrcpSynthesizerChannels(SessionDescription sd) {
		return getChannels(sd, SDP_MRCP_PROTOCOL, SDP_SYNTH_RESOURCE);
	}
	
	public List<MediaDescription> getMrcpRecognizorChannels(SessionDescription sd) {
		return getChannels(sd, SDP_MRCP_PROTOCOL, SDP_RECOG_RESOURCE);
	}
	
	private List<MediaDescription> getChannels(SessionDescription sd, String protocol, String type) {
		List<MediaDescription> channels = new ArrayList<MediaDescription>();
		try {
			@SuppressWarnings("unchecked")
			Enumeration<MediaDescription> e = sd.getMediaDescriptions(true).elements();
			while(e.hasMoreElements()) {
				MediaDescription md = (MediaDescription) e.nextElement();
				if(md.getMedia().getProtocol().equals(protocol)) {
					if(md.getAttribute(SDP_SETUP_ATTR_NAME).equals(SDP_PASSIVE_SETUP)) {
						if(md.getAttribute(SDP_RESOURCE_ATTR_NAME) != null && md.getAttribute(SDP_RESOURCE_ATTR_NAME).equals(type)) {
							channels.add(md);
						} else {
							if(md.getAttribute(SDP_CHANNEL_ATTR_NAME) != null && md.getAttribute(SDP_CHANNEL_ATTR_NAME).endsWith(type)) {
								channels.add(md);
							}
						}
					}
				}
			}
		} catch (SdpException e) {
			logger.error("Unable to get channels. ", e);
		}
		return channels;
	}
	
	public List<MediaDescription> getAudioChannsForRecogChann(SessionDescription sd, MediaDescription recogChann) {
		List<MediaDescription> channels = new ArrayList<MediaDescription>();
		
		String id = null;
		String protocol = null;
		String attributeName = null;
		
		try {
			if(recogChann.getMedia().getProtocol().equals(SDP_MRCP_PROTOCOL)) {
				id = recogChann.getAttribute(SDP_CMID_ATTR_NAME);
				protocol = SDP_RTP_PROTOCOL;
				attributeName = SDP_MID_ATTR_NAME;
				@SuppressWarnings("unchecked")
				Enumeration<MediaDescription> e = sd.getMediaDescriptions(true).elements();
				while(e.hasMoreElements()) {
					MediaDescription md = e.nextElement();
					if(md.getMedia().getProtocol().equals(protocol)) {
						if(md.getAttribute(attributeName).equalsIgnoreCase(id)) {
							channels.add(md);
						}
					}
				}
			} else {
				throw new SdpException(recogChann.toString() + " not a MRCP control channel");
			}
		} catch (Exception e) {
			logger.error("Unable to get Audio Channels for Recognition Channels. ", e);
		}
		return channels;
	}
	
	@SuppressWarnings("unchecked")
	public String constructResourceMessage(byte[] sdp) {
		try {
			SessionDescription sd = sdpFactory.createSessionDescription(new String(sdp));
			String localHost;
			if(sd.getConnection() != null) {
				localHost = sd.getConnection().getAddress();
			}
			else localHost = ((MediaDescription)sd.getMediaDescriptions(true).get(0)).getConnection().getAddress();
			int localPort = 0;
			Vector<String> formats = null;
			Vector<Attribute> rtpmap = new Vector<Attribute>();
			for(MediaDescription md : this.getChannels(sd, SDP_RTP_PROTOCOL)) {
				localPort = md.getMedia().getMediaPort();
				formats = ((MediaField)md.getMedia()).getMediaFormats(true);
				for(Attribute attr : (Vector<Attribute>)md.getAttributes(false)) {
					if(attr.getName().equals(SDP_RTP_MAP)) {
						rtpmap.add(attr);
					}
				}
			}
			MediaDescription rtpChannel = creatRtpChannelRequest(localPort, formats, localHost, rtpmap); 
			MediaDescription synthControlChannel = createMrcpChannelRequest(MrcpResourceType.SPEECHSYNTH);
			MediaDescription recogControlChannel = createMrcpChannelRequest(MrcpResourceType.SPEECHRECOG);
			Vector<MediaDescription> vector = new Vector<MediaDescription>();
			vector.add(synthControlChannel);
			vector.add(recogControlChannel);
			vector.add(rtpChannel);
			sd.setMediaDescriptions(vector);
			return sd.toString();
		} catch (SdpException e) {
			logger.error("Unable to construct resource message. ", e);
		}

		return null;
	}
	
	private MediaDescription createMrcpChannelRequest(MrcpResourceType resourceType)
            throws SdpException {

        MediaDescription md = new MediaDescriptionImpl();
        Media m = new MediaField();

        try {
        	m.setMediaPort(9);

        	m.setMediaType(SDP_APPLICATION_MEDIA);
        	m.setProtocol(SDP_MRCP_PROTOCOL);
        	md.setMedia(m);
        	md.setAttribute(SDP_SETUP_ATTR_NAME, SDP_ACTIVE_SETUP);
        	md.setAttribute(SDP_CONNECTION_ATTR_NAME, SDP_NEW_CONNECTION);
        	md.setAttribute(SDP_CMID_ATTR_NAME, "1");

            if (resourceType == MrcpResourceType.SPEECHRECOG) {
            	md.setAttribute(SDP_RESOURCE_ATTR_NAME, SDP_RECOG_RESOURCE);
            } else if (resourceType == MrcpResourceType.SPEECHSYNTH) {
            	md.setAttribute(SDP_RESOURCE_ATTR_NAME, SDP_SYNTH_RESOURCE);
            }
        } catch (SdpException e) {
        	logger.error("Unable to create MRCP Channel Request. ", e);
        }
        return md;
    }
	
	public SessionDescription constructInviteResponseToClient(SessionDescription sd, int remoteRtpPort, Vector<String> formats, Vector<Attribute> rtpmap) {
		MediaDescription rtpChannel = createRtpChannelRequest(remoteRtpPort, formats, rtpmap);
		Vector<MediaDescription> vector = new Vector<MediaDescription>();
		vector.add(rtpChannel);
		try {
			sd.setMediaDescriptions(vector);
			return sd;
		} catch (SdpException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private MediaDescription createRtpChannelRequest(int localPort, Vector<String> formats, Vector<Attribute> rtpmap) {
		MediaDescription md = new MediaDescriptionImpl();
		Media m = new MediaField();
		
		try {
			m.setMediaPort(localPort);
			m.setMediaType(SDP_AUDIO_MEDIA);
			m.setProtocol(SDP_RTP_PROTOCOL);
			m.setMediaFormats(formats);
			md.setMedia(m);
			md.setAttributes(rtpmap);
			
			md.setAttribute(SDP_MID_ATTR_NAME, "1");
			md.setAttribute("sendrecv", null);
		} catch(SdpException e) {
			logger.error("Unable to create RTP Channel Request. ", e);
		}
		return md;
	}
	
	private MediaDescription creatRtpChannelRequest(int localPort, Vector<String> formats, String rtpHost, Vector<Attribute> rtpmap) {
		MediaDescription md = new MediaDescriptionImpl();
		Media m = new MediaField();
		Connection c = new ConnectionField();
		
		try {
			m.setMediaPort(localPort);
			m.setMediaType(SDP_AUDIO_MEDIA);
			m.setProtocol(SDP_RTP_PROTOCOL);
			m.setMediaFormats(formats);
			md.setMedia(m);
			md.setAttributes(rtpmap);
			
			md.setAttribute(SDP_MID_ATTR_NAME, "1");
			md.setAttribute("sendrecv", null);
			
			c.setAddress(rtpHost);
			c.setAddressType("IP4");
			c.setNetworkType("IN");
			md.setConnection(c);
		} catch(SdpException e) {
			logger.error("Unable to create RTP Channel Request. ", e);
		}
		
		return md;
	}
	
	private List<MediaDescription> getChannels(SessionDescription sd, String protocol) {
		List<MediaDescription> channs = new ArrayList<MediaDescription>();
		try {
			@SuppressWarnings("rawtypes")
			Enumeration e = sd.getMediaDescriptions(true).elements();
			while(e.hasMoreElements()) {
				MediaDescription md = (MediaDescription) e.nextElement();
				if(md.getMedia().getMediaType().equals(SDP_AUDIO_MEDIA) && md.getMedia().getProtocol().equals(protocol)) {
					channs.add(md);
				}
			}
		} catch (SdpException e) {
			logger.error("Unable to get Channels. ", e);
		}
		return channs;
	}

}

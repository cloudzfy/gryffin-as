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

import org.cloudzfy.slee.resource.mrcp.MrcpChannelActivity;
import org.jvoicexml.client.BasicConnectionInformation;

public class MrcpConnectionInformation extends BasicConnectionInformation {

	private static MrcpConnectionInformation connection = null;
	
	private static final long serialVersionUID = 5431364855452437193L;
	
	private MrcpChannelActivity synthChannel;
	private MrcpChannelActivity recogChannel;
	
	private MrcpConnectionInformation() {
		super("mrcp", "mrcp", "mrcp");
	}
	
	public static MrcpConnectionInformation getInstance() {
		if(connection == null) {
			connection = new MrcpConnectionInformation();
		}
		return connection;
	}

	public void setSynthChannel(MrcpChannelActivity synthChannel) {
		this.synthChannel = synthChannel;
	}
	
	public void setRecogChannel(MrcpChannelActivity recogChannel) {
		this.recogChannel = recogChannel;
	}
	
	public MrcpChannelActivity getSynthChannel() {
		return synthChannel;
	}
	
	public MrcpChannelActivity getRecogChannel() {
		return recogChannel;
	}
	
}

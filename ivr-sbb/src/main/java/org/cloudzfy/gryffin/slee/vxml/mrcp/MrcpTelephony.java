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

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;

import javax.sound.sampled.AudioFormat;

import org.apache.log4j.Logger;
import org.jvoicexml.CallControlProperties;
import org.jvoicexml.ConnectionInformation;
import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.implementation.ObservableTelephony;
import org.jvoicexml.implementation.SpokenInput;
import org.jvoicexml.implementation.SynthesizedOutput;
import org.jvoicexml.implementation.Telephony;
import org.jvoicexml.implementation.TelephonyListener;

public class MrcpTelephony implements Telephony, ObservableTelephony {

	private static Logger logger = Logger.getLogger(MrcpTelephony.class);
	private Collection<TelephonyListener> listeners;
	
	public MrcpTelephony() {
		listeners = new ArrayList<TelephonyListener>();
	}
	
	@Override
	public void activate() throws NoresourceError {
		logger.info("Activating call control...");
	}

	@Override
	public void close() {
		logger.info("Closing Telephony...");
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
	}

	@Override
	public void passivate() throws NoresourceError {
		logger.info("Passivating telephony...");
		listeners.clear();
		logger.info("Telephony passivated.");
	}

	@Override
	public void connect(ConnectionInformation client) throws IOException {
		MrcpConnectionInformation connectionInfo = (MrcpConnectionInformation) client;
		logger.info("Connecting to " + connectionInfo + "...");
	}

	@Override
	public void disconnect(ConnectionInformation client) {
		if(client instanceof MrcpConnectionInformation) {
			logger.info("Disconnected Telecphony from " + (MrcpConnectionInformation)client + ".");
		}
	}

	@Override
	public void addListener(TelephonyListener listener) {
		listeners.add(listener);
	}

	@Override
	public void removeListener(TelephonyListener listener) {
		listeners.remove(listener);
	}

	@Override
	public AudioFormat getRecordingAudioFormat() {
		return null;
	}

	@Override
	public void play(SynthesizedOutput arg0, CallControlProperties arg1)
			throws NoresourceError, IOException {
		
	}

	@Override
	public void record(SpokenInput arg0, CallControlProperties arg1)
			throws NoresourceError, IOException {
		
	}

	@Override
	public void startRecording(SpokenInput arg0, OutputStream arg1,
			CallControlProperties arg2) throws NoresourceError, IOException {
		
	}

	@Override
	public void stopPlay() throws NoresourceError {
		
	}

	@Override
	public void stopRecording() throws NoresourceError {
		
	}

	@Override
	public void transfer(String destination) throws NoresourceError {
		
	}

}

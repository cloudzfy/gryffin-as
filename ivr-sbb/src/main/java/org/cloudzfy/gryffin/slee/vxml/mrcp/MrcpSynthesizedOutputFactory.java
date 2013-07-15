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

import org.jvoicexml.event.error.NoresourceError;
import org.jvoicexml.implementation.ResourceFactory;
import org.jvoicexml.implementation.SynthesizedOutput;

public class MrcpSynthesizedOutputFactory implements ResourceFactory<SynthesizedOutput> {

	private String type;
	private int instances;
	
	public MrcpSynthesizedOutputFactory() {
		this.type = "mrcp";
		this.instances = 1;
	}
	
	@Override
	public SynthesizedOutput createResource() throws NoresourceError {
		return new MrcpSynthesizedOutput();
	}

	@Override
	public int getInstances() {
		return instances;
	}
	
	public void setInstences(int instances) {
		this.instances = instances;
	}

	@Override
	public Class<SynthesizedOutput> getResourceType() {
		return SynthesizedOutput.class;
	}

	@Override
	public String getType() {
		return type;
	}

}

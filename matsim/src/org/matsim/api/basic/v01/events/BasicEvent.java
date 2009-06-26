/* *********************************************************************** *
 * project: org.matsim.*
 * BasicEventI.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package org.matsim.api.basic.v01.events;

import java.util.Map;

/**
 * The most basic interface for MATSim Events.
 * Every event must have at least a timestamp.
 * 
 * @author mrieser
 */
public interface BasicEvent {
	
	/** @return the timestamp of this event */
	public double getTime();

	/** Returns a map of all the attribute names and values needed for serializing the event.
	 *
	 * @return Map of attribute-names and attribute-values that describe that event */
	public Map<String, String> getAttributes();

}

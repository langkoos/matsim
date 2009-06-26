/* *********************************************************************** *
 * project: org.matsim.*
 * AgentEvent.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007, 2008 by the members listed in the COPYING,  *
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

package org.matsim.core.events;

import java.util.Map;

import org.matsim.api.basic.v01.Id;
import org.matsim.api.basic.v01.events.BasicAgentEvent;
import org.matsim.core.api.network.Link;
import org.matsim.core.api.population.Leg;
import org.matsim.core.api.population.Person;

public abstract class AgentEvent extends PersonEvent implements BasicAgentEvent {

	public static final String ATTRIBUTE_LINK = "link";	

	private Leg leg;

	private final Id linkId;

	AgentEvent(final double time, final Person agent, final Link link, final Leg leg) {
		super(time, agent);
		this.linkId = link.getId();
		this.leg = leg;
	}

	AgentEvent(final double time, final Id agentId, final Id linkId) {
		super(time, agentId);
		this.linkId = linkId;
	}

	@Override
	public Map<String, String> getAttributes() {
		Map<String, String> attr = super.getAttributes();
		attr.put(ATTRIBUTE_LINK, this.linkId.toString());
		return attr;
	}

	public Leg getLeg() {
		return this.leg;
	}
	
	public Id getLinkId() {
		return this.linkId;
	}
	
}

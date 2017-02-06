/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2015 by the members listed in the COPYING,        *
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

package org.matsim.contrib.drt.tasks;

import java.util.Set;

import org.matsim.contrib.drt.DrtRequest;
import org.matsim.contrib.dvrp.path.VrpPathWithTravelData;
import org.matsim.contrib.dvrp.schedule.DriveTaskImpl;

/**
 * @author jbischoff
 *
 */
public class DrtDriveWithPassengersTask extends DriveTaskImpl implements DrtTaskWithRequests {
	private final Set<DrtRequest> requests;

	public DrtDriveWithPassengersTask(Set<DrtRequest> requests, VrpPathWithTravelData path) {
		super(path);
		this.requests = requests;
	}

	@Override
	public DrtTaskType getDrtTaskType() {
		return DrtTaskType.DRIVE_WITH_PASSENGERS;
	}

	@Override
	public Set<DrtRequest> getRequests() {
		return requests;
	}

	@Override
	public void removeRequest(DrtRequest request) {
		this.requests.remove(request);
	}

	@Override
	public void removeAllRequests() {
		this.requests.clear();
	}
}
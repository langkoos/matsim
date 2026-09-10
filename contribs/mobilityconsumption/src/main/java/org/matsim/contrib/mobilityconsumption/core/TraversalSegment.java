package org.matsim.contrib.mobilityconsumption.core;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.vehicles.Vehicle;

/**
 * One vehicle's presence on one link between two events.
 *
 * @param linkId        the link
 * @param vehicleId     the vehicle
 * @param driverId      the driver, from the vehicle-enters-traffic event; may be null when unknown
 * @param mode          network mode of the vehicle on this leg
 * @param kind          departure, full or arrival segment
 * @param enterTime     time the segment starts, seconds
 * @param leaveTime     time the segment ends, seconds; not before enterTime
 * @param distance      metres travelled on the link during the segment
 * @param freeFlowTime  seconds the same distance would take at free flow
 * @param spaceOccupied road space occupied by this vehicle, lambda, in metres
 */
public record TraversalSegment(
	Id<Link> linkId,
	Id<Vehicle> vehicleId,
	Id<Person> driverId,
	String mode,
	SegmentKind kind,
	double enterTime,
	double leaveTime,
	double distance,
	double freeFlowTime,
	double spaceOccupied) {

	public TraversalSegment {
		if (leaveTime < enterTime) {
			throw new IllegalArgumentException("leaveTime " + leaveTime + " before enterTime " + enterTime);
		}
		if (distance < 0 || freeFlowTime < 0 || spaceOccupied < 0) {
			throw new IllegalArgumentException("distance, freeFlowTime and spaceOccupied must be non-negative");
		}
	}

	/** Time spent on the link, seconds. */
	public double travelTime() {
		return leaveTime - enterTime;
	}
}

package org.matsim.contrib.mobilityconsumption.core;

import java.util.Set;

/**
 * What the {@link TraversalSegmentCollector} records.
 *
 * @param networkModes             network modes (from the vehicle-enters-traffic event) to include
 * @param excludeTransitVehicles   drop vehicles seen in a {@code TransitDriverStartsEvent}
 * @param includeDepartureSegments record buffer waiting on the departure link
 * @param includeArrivalSegments   record the traversal of the arrival link
 * @param vehicleLengthSource      where lambda comes from
 * @param timeStepSize             the mobsim time step, needed to reproduce QSim's free-flow travel time
 */
public record CollectorSettings(
	Set<String> networkModes,
	boolean excludeTransitVehicles,
	boolean includeDepartureSegments,
	boolean includeArrivalSegments,
	VehicleLengthSource vehicleLengthSource,
	double timeStepSize) {

	public static final CollectorSettings CAR_DEFAULTS =
		new CollectorSettings(Set.of("car"), true, true, true, VehicleLengthSource.fixed, 1.0);

	public CollectorSettings {
		networkModes = Set.copyOf(networkModes);
		if (timeStepSize <= 0) {
			throw new IllegalArgumentException("timeStepSize must be positive");
		}
	}
}

package org.matsim.contrib.mobilityconsumption.core;

import java.util.Map;
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
 * @param spaceByVehicleType       road space (lambda, metres) per vehicle type id, overriding the length source;
 *                                 the only way to give transit vehicle types a realistic footprint when the
 *                                 scenario's vehicle file carries default lengths
 */
public record CollectorSettings(
	Set<String> networkModes,
	boolean excludeTransitVehicles,
	boolean includeDepartureSegments,
	boolean includeArrivalSegments,
	VehicleLengthSource vehicleLengthSource,
	double timeStepSize,
	Map<String, Double> spaceByVehicleType) {

	public static final CollectorSettings CAR_DEFAULTS =
		new CollectorSettings(Set.of("car"), true, true, true, VehicleLengthSource.fixed, 1.0);

	public CollectorSettings(Set<String> networkModes, boolean excludeTransitVehicles, boolean includeDepartureSegments,
			boolean includeArrivalSegments, VehicleLengthSource vehicleLengthSource, double timeStepSize) {
		this(networkModes, excludeTransitVehicles, includeDepartureSegments, includeArrivalSegments, vehicleLengthSource,
			timeStepSize, Map.of());
	}

	public CollectorSettings {
		networkModes = Set.copyOf(networkModes);
		spaceByVehicleType = Map.copyOf(spaceByVehicleType);
		if (timeStepSize <= 0) {
			throw new IllegalArgumentException("timeStepSize must be positive");
		}
	}
}

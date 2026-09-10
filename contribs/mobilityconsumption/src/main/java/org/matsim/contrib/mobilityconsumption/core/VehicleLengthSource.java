package org.matsim.contrib.mobilityconsumption.core;

/** Where the road space occupied by a vehicle, lambda, comes from. */
public enum VehicleLengthSource {
	/** The configured vehicle length plus spacing for every vehicle (paper default, 11.12 m). */
	fixed,
	/**
	 * {@code VehicleType.getLength()} of the simulated vehicle, used as lambda directly. MATSim's vehicle length is
	 * the space a vehicle takes in the queue, so the default 7.5 m already includes spacing. Falls back to the fixed
	 * value when the vehicle type is unknown.
	 */
	vehicleType
}

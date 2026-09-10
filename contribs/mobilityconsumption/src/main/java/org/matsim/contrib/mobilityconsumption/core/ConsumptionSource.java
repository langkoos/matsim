package org.matsim.contrib.mobilityconsumption.core;

/**
 * What consumed the road space-time. Only flowing traffic is implemented; the other values reserve the
 * seam for parked vehicles and transit so that outputs keep their shape when those are added.
 */
public enum ConsumptionSource {
	FLOW,
	PARKING,
	TRANSIT
}

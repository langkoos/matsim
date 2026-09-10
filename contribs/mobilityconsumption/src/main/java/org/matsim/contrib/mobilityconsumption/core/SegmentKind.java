package org.matsim.contrib.mobilityconsumption.core;

/**
 * Where on a leg a traversal segment sits. MATSim's queue simulation places a departing vehicle at the
 * downstream end of its first link and lets an arriving vehicle drive its last link in full, so the first
 * and last segments of a leg differ from a plain link-to-link traversal.
 */
public enum SegmentKind {
	/** From {@code VehicleEntersTrafficEvent} to the first {@code LinkLeaveEvent}: buffer waiting, zero distance. */
	DEPARTURE,
	/** From {@code LinkEnterEvent} to {@code LinkLeaveEvent}: a full link traversal. */
	FULL,
	/** From {@code LinkEnterEvent} to {@code VehicleLeavesTrafficEvent}: the last link of a leg, driven in full. */
	ARRIVAL
}

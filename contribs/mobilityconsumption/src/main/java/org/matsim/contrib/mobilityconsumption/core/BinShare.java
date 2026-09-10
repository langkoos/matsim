package org.matsim.contrib.mobilityconsumption.core;

/**
 * The part of a segment's consumption that falls into one time bin.
 *
 * @param bin        bin index, or the outside bin
 * @param consumption metre-seconds consumed in the bin
 * @param excess      metre-seconds of that consumption attributable to travel above free flow
 * @param distance    metres travelled in the bin
 * @param travelTime  seconds spent in the bin
 * @param passengerDistance passenger-metres in the bin (distance times occupancy)
 * @param passengerTime     passenger-seconds in the bin (travel time times occupancy)
 */
public record BinShare(int bin, double consumption, double excess, double distance, double travelTime,
	double passengerDistance, double passengerTime) {

	/** A share carried by one occupant. */
	public BinShare(int bin, double consumption, double excess, double distance, double travelTime) {
		this(bin, consumption, excess, distance, travelTime, distance, travelTime);
	}
}

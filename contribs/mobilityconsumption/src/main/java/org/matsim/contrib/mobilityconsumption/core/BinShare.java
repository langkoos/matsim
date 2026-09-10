package org.matsim.contrib.mobilityconsumption.core;

/**
 * The part of a segment's consumption that falls into one time bin.
 *
 * @param bin        bin index, or the outside bin
 * @param consumption metre-seconds consumed in the bin
 * @param excess      metre-seconds of that consumption attributable to travel above free flow
 * @param distance    metres travelled in the bin
 * @param travelTime  seconds spent in the bin
 */
public record BinShare(int bin, double consumption, double excess, double distance, double travelTime) {
}

package org.matsim.contrib.mobilityconsumption.core;

import java.util.Arrays;

/**
 * Running sums per time bin, in raw (unscaled) internal units: metre-seconds, metres, seconds and counts.
 * Index {@code binCount} is the bin for everything outside the analysis window.
 */
public final class BinTotals {

	private final double[] consumption;
	private final double[] excess;
	private final double[] distance;
	private final double[] travelTime;
	private final long[] segments;

	public BinTotals(int binCountIncludingOutside) {
		this.consumption = new double[binCountIncludingOutside];
		this.excess = new double[binCountIncludingOutside];
		this.distance = new double[binCountIncludingOutside];
		this.travelTime = new double[binCountIncludingOutside];
		this.segments = new long[binCountIncludingOutside];
	}

	public void add(BinShare share) {
		consumption[share.bin()] += share.consumption();
		excess[share.bin()] += share.excess();
		distance[share.bin()] += share.distance();
		travelTime[share.bin()] += share.travelTime();
	}

	/** Counts a segment against the bin in which it started. */
	public void countSegment(int bin) {
		segments[bin]++;
	}

	/** Sets every sum back to zero. */
	public void clear() {
		Arrays.fill(consumption, 0);
		Arrays.fill(excess, 0);
		Arrays.fill(distance, 0);
		Arrays.fill(travelTime, 0);
		Arrays.fill(segments, 0);
	}

	public int size() {
		return consumption.length;
	}

	public double consumption(int bin) {
		return consumption[bin];
	}

	public double excess(int bin) {
		return excess[bin];
	}

	public double distance(int bin) {
		return distance[bin];
	}

	public double travelTime(int bin) {
		return travelTime[bin];
	}

	public long segments(int bin) {
		return segments[bin];
	}

	public double totalConsumption() {
		return sum(consumption);
	}

	public double totalExcess() {
		return sum(excess);
	}

	public double totalDistance() {
		return sum(distance);
	}

	public double totalTravelTime() {
		return sum(travelTime);
	}

	public long totalSegments() {
		long total = 0;
		for (long s : segments) {
			total += s;
		}
		return total;
	}

	private static double sum(double[] values) {
		double total = 0;
		for (double v : values) {
			total += v;
		}
		return total;
	}
}

package org.matsim.contrib.mobilityconsumption.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns traversal segments into consumption, both as a whole and split across time bins.
 *
 * <p>Consumption of a segment is {@code lambda * T + tau * D}; its excess part is
 * {@code max(lambda * (T - T0), 0)}. A segment that spans several bins is divided assuming uniform speed on
 * the link, so time, distance, consumption and excess are all shared in proportion to the time in each bin
 * (equations 8 to 10 of Ai et al.).
 */
public final class MobilityConsumptionCalculator {

	private final MobilityConsumptionParameters parameters;

	public MobilityConsumptionCalculator(MobilityConsumptionParameters parameters) {
		this.parameters = parameters;
	}

	public MobilityConsumptionParameters getParameters() {
		return parameters;
	}

	/** Total consumption of a segment in metre-seconds. */
	public double consumption(TraversalSegment segment) {
		return segment.spaceOccupied() * segment.travelTime() + parameters.reactionTime() * segment.distance();
	}

	/** Excess consumption of a segment in metre-seconds; zero when the segment was at or above free-flow speed. */
	public double excess(TraversalSegment segment) {
		return Math.max(segment.spaceOccupied() * (segment.travelTime() - segment.freeFlowTime()), 0);
	}

	/**
	 * Splits a segment across the time bins it overlaps. A zero-duration segment goes wholly into the bin of its
	 * enter time. The returned shares sum to the segment's consumption, excess, distance and travel time.
	 */
	public List<BinShare> split(TraversalSegment segment) {
		double total = consumption(segment);
		double totalExcess = excess(segment);
		double duration = segment.travelTime();
		List<BinShare> shares = new ArrayList<>();
		if (duration <= 0) {
			shares.add(new BinShare(parameters.binIndex(segment.enterTime()), total, totalExcess, segment.distance(), 0,
				segment.distance() * segment.occupancy(), 0));
			return shares;
		}
		double cursor = segment.enterTime();
		while (cursor < segment.leaveTime()) {
			int bin = parameters.binIndex(cursor);
			double boundary = nextBoundary(cursor, bin);
			double end = Math.min(boundary, segment.leaveTime());
			double fraction = (end - cursor) / duration;
			double distance = fraction * segment.distance();
			shares.add(new BinShare(bin, fraction * total, fraction * totalExcess, distance, end - cursor,
				distance * segment.occupancy(), (end - cursor) * segment.occupancy()));
			cursor = end;
		}
		return shares;
	}

	private double nextBoundary(double time, int bin) {
		if (bin == parameters.outsideBin()) {
			// Before the window: the next boundary is the window start. After it: no further boundary.
			return time < parameters.analysisStart() ? parameters.analysisStart() : Double.POSITIVE_INFINITY;
		}
		return parameters.binEnd(bin);
	}
}

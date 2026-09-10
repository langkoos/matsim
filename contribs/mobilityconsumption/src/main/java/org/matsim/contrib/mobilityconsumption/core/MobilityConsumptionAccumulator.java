package org.matsim.contrib.mobilityconsumption.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;

/**
 * Collects consumption per link and time bin, per mode and time bin, and network-wide, plus diagnostics.
 * All sums are raw simulated values; scaling to the full population is applied when writing.
 *
 * <p>One exception is built in here: MATSim runs the full transit timetable regardless of the population
 * sample, so a transit vehicle's own quantities (consumption, excess, distance, time) are pre-divided by the
 * upscale factor, which the uniform upscaling at write time undoes. Its passengers are sampled like everyone
 * else and are upscaled normally.
 * Not thread-safe: MATSim delivers events to a handler from one thread.
 */
public final class MobilityConsumptionAccumulator {

	private final MobilityConsumptionParameters parameters;
	private final MobilityConsumptionCalculator calculator;
	private final Map<Id<Link>, BinTotals> links = new TreeMap<>();
	private final Map<String, BinTotals> modes = new TreeMap<>();
	private final BinTotals network;
	private long abortedSegments;

	public MobilityConsumptionAccumulator(MobilityConsumptionParameters parameters) {
		this.parameters = parameters;
		this.calculator = new MobilityConsumptionCalculator(parameters);
		this.network = new BinTotals(parameters.binCount() + 1);
	}

	public MobilityConsumptionParameters getParameters() {
		return parameters;
	}

	public MobilityConsumptionCalculator getCalculator() {
		return calculator;
	}

	/** Adds a segment and returns the bin shares it was split into. */
	public List<BinShare> add(TraversalSegment segment) {
		List<BinShare> shares = calculator.split(segment);
		if (segment.source() == ConsumptionSource.TRANSIT) {
			shares = shares.stream().map(this::unsampledVehicle).toList();
		}
		BinTotals link = links.computeIfAbsent(segment.linkId(), id -> new BinTotals(parameters.binCount() + 1));
		BinTotals mode = modes.computeIfAbsent(segment.mode(), m -> new BinTotals(parameters.binCount() + 1));
		for (BinShare share : shares) {
			link.add(share);
			mode.add(share);
			network.add(share);
		}
		int startBin = parameters.binIndex(segment.enterTime());
		link.countSegment(startBin);
		mode.countSegment(startBin);
		network.countSegment(startBin);
		return shares;
	}

	/** Vehicle quantities of an unsampled fleet, pre-divided so that the write-time upscaling leaves them at 1x. */
	private BinShare unsampledVehicle(BinShare s) {
		double f = parameters.sampleSize();
		return new BinShare(s.bin(), s.consumption() * f, s.excess() * f, s.distance() * f, s.travelTime() * f,
			s.passengerDistance(), s.passengerTime());
	}

	/** Records a segment that was cut short by a vehicle abort and therefore not added. */
	public void recordAborted() {
		abortedSegments++;
	}

	public Map<Id<Link>, BinTotals> links() {
		return Collections.unmodifiableMap(links);
	}

	public Map<String, BinTotals> modes() {
		return Collections.unmodifiableMap(modes);
	}

	public BinTotals network() {
		return network;
	}

	public long abortedSegments() {
		return abortedSegments;
	}

	/** Forgets everything; used at the start of an iteration. */
	public void reset() {
		links.clear();
		modes.clear();
		network.clear();
		abortedSegments = 0;
	}
}

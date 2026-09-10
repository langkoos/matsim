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

package org.matsim.contrib.mobilityconsumption.core;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

/**
 * Road space-time supplied by the network: per link and bin {@code length * lanes(t) * binLength}, in
 * metre-seconds (equations 6 and 12 of Ai et al.). Lanes are read at the bin start, so time-variant
 * networks are honoured. Only links that allow at least one of the analysed network modes supply space:
 * transit-only or bicycle-only links are not road space a car could use. Production is never scaled by
 * sample size; consumption is scaled up instead.
 */
public final class MobilityProduction {

	private final MobilityConsumptionParameters parameters;
	private final Map<Id<Link>, double[]> perLink = new TreeMap<>();
	private final double[] network;

	/** Production over every link of the network, regardless of allowed modes. */
	public MobilityProduction(Network net, MobilityConsumptionParameters parameters) {
		this(net, parameters, Set.of());
	}

	/**
	 * @param modes network modes whose links supply road space; an empty set means every link
	 */
	public MobilityProduction(Network net, MobilityConsumptionParameters parameters, Set<String> modes) {
		this.parameters = parameters;
		this.network = new double[parameters.binCount() + 1];
		for (Link link : net.getLinks().values()) {
			if (!modes.isEmpty() && Collections.disjoint(link.getAllowedModes(), modes)) {
				continue;
			}
			double[] bins = computeBins(link, parameters);
			perLink.put(link.getId(), bins);
			for (int b = 0; b < bins.length; b++) {
				network[b] += bins[b];
			}
		}
	}

	static double[] computeBins(Link link, MobilityConsumptionParameters parameters) {
		double[] bins = new double[parameters.binCount() + 1];
		for (int b = 0; b < parameters.binCount(); b++) {
			double start = parameters.binStart(b);
			bins[b] = link.getLength() * link.getNumberOfLanes(start) * (parameters.binEnd(b) - start);
		}
		return bins;
	}

	public MobilityConsumptionParameters getParameters() {
		return parameters;
	}

	/** Production of a link in a bin; zero for the outside bin and for unknown links. */
	public double link(Id<Link> linkId, int bin) {
		double[] bins = perLink.get(linkId);
		return bins == null ? 0 : bins[bin];
	}

	/** Production of a link over the whole analysis window. */
	public double linkTotal(Id<Link> linkId) {
		double[] bins = perLink.get(linkId);
		if (bins == null) {
			return 0;
		}
		double total = 0;
		for (double v : bins) {
			total += v;
		}
		return total;
	}

	public double network(int bin) {
		return network[bin];
	}

	public double networkTotal() {
		double total = 0;
		for (double v : network) {
			total += v;
		}
		return total;
	}

	public Map<Id<Link>, double[]> perLink() {
		return Collections.unmodifiableMap(perLink);
	}
}

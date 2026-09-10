package org.matsim.contrib.mobilityconsumption.io;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.contrib.mobilityconsumption.core.BinTotals;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionAccumulator;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionCalculator;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionParameters;
import org.matsim.contrib.mobilityconsumption.core.MobilityProduction;
import org.matsim.contrib.mobilityconsumption.core.TraversalSegment;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Time;

/**
 * Writes the mobility consumption tables. Consumption values are scaled to the full population by the
 * parameters' upscale factor; production is not. Internal metre-seconds become kilometre-hours.
 */
public final class MobilityConsumptionWriter {

	public static final List<String> LINK_HEADER = List.of("link_id", "length_m", "lanes", "mc_kmh", "mc_excess_kmh", "mp_kmh",
		"utilization", "excess_ratio", "vehicle_km", "vehicle_h", "segments");
	public static final List<String> LINK_BIN_HEADER = List.of("link_id", "bin", "bin_start", "time", "mc_kmh", "mc_excess_kmh",
		"mp_kmh", "utilization", "excess_ratio", "vehicle_km", "vehicle_h", "segments");
	public static final List<String> NETWORK_BIN_HEADER = List.of("mode", "bin", "bin_start", "time", "mc_kmh", "mc_excess_kmh",
		"mp_kmh", "utilization", "excess_ratio", "vehicle_km", "vehicle_h", "segments");
	public static final List<String> STATS_HEADER = List.of("iteration", "sample_size", "mc_kmh", "mc_excess_kmh", "excess_ratio",
		"mp_kmh", "mc_mp_ratio", "vehicle_km", "vehicle_h", "segments", "aborted_segments");
	public static final List<String> SEGMENT_HEADER = List.of("vehicle_id", "driver_id", "link_id", "mode", "kind", "enter_time",
		"leave_time", "distance_m", "free_flow_time_s", "space_occupied_m", "mc_kmh", "mc_excess_kmh");
	public static final String ALL_MODES = "all";
	public static final String OUTSIDE = "outside";

	private final CSVFormat format;

	public MobilityConsumptionWriter(String delimiter) {
		this.format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setRecordSeparator("\n").get();
	}

	public CSVFormat getFormat() {
		return format;
	}

	/** Opens a printer on a path; compression follows the file extension. */
	public CSVPrinter open(String path) {
		try {
			return new CSVPrinter(IOUtils.getBufferedWriter(path), format);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Opens a printer that appends to an existing (uncompressed) file. */
	public CSVPrinter openAppend(String path) {
		try {
			BufferedWriter writer = IOUtils.getBufferedWriter(IOUtils.getFileUrl(path), IOUtils.CHARSET_UTF8, true);
			return new CSVPrinter(writer, format);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** One row per link that saw traffic, over the whole day. */
	public void writeLinks(String path, MobilityConsumptionAccumulator acc, MobilityProduction mp, Network network) {
		MobilityConsumptionParameters p = acc.getParameters();
		double up = p.upscaleFactor();
		try (CSVPrinter printer = open(path)) {
			printer.printRecord(LINK_HEADER);
			for (Map.Entry<Id<Link>, BinTotals> e : acc.links().entrySet()) {
				Link link = network.getLinks().get(e.getKey());
				BinTotals t = e.getValue();
				double mc = up * t.totalConsumption();
				double excess = up * t.totalExcess();
				double production = mp.linkTotal(e.getKey());
				printer.printRecord(e.getKey(), link == null ? "" : link.getLength(),
					link == null ? "" : link.getNumberOfLanes(), kmh(mc), kmh(excess), kmh(production),
					ratio(mc, production), ratio(excess, mc), up * t.totalDistance() / 1000.0,
					up * t.totalTravelTime() / 3600.0, t.totalSegments());
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** One row per link and bin with consumption, including the outside bin when it is non-empty. */
	public void writeLinkBins(String path, MobilityConsumptionAccumulator acc, MobilityProduction mp) {
		MobilityConsumptionParameters p = acc.getParameters();
		double up = p.upscaleFactor();
		try (CSVPrinter printer = open(path)) {
			printer.printRecord(LINK_BIN_HEADER);
			for (Map.Entry<Id<Link>, BinTotals> e : acc.links().entrySet()) {
				BinTotals t = e.getValue();
				for (int b = 0; b < t.size(); b++) {
					if (t.consumption(b) == 0 && t.segments(b) == 0) {
						continue;
					}
					double mc = up * t.consumption(b);
					double excess = up * t.excess(b);
					double production = mp.link(e.getKey(), b);
					printer.printRecord(e.getKey(), binLabel(p, b), binStart(p, b), binTime(p, b), kmh(mc),
						kmh(excess), kmh(production), ratio(mc, production), ratio(excess, mc),
						up * t.distance(b) / 1000.0, up * t.travelTime(b) / 3600.0, t.segments(b));
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Network totals per bin, for all modes together and for each mode. */
	public void writeNetworkBins(String path, MobilityConsumptionAccumulator acc, MobilityProduction mp) {
		MobilityConsumptionParameters p = acc.getParameters();
		double up = p.upscaleFactor();
		try (CSVPrinter printer = open(path)) {
			printer.printRecord(NETWORK_BIN_HEADER);
			writeModeRows(printer, ALL_MODES, acc.network(), mp, p, up);
			for (Map.Entry<String, BinTotals> e : acc.modes().entrySet()) {
				writeModeRows(printer, e.getKey(), e.getValue(), mp, p, up);
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void writeModeRows(CSVPrinter printer, String mode, BinTotals t, MobilityProduction mp,
			MobilityConsumptionParameters p, double up) throws IOException {
		for (int b = 0; b < t.size(); b++) {
			if (b == p.outsideBin() && t.consumption(b) == 0 && t.segments(b) == 0) {
				continue;
			}
			double mc = up * t.consumption(b);
			double excess = up * t.excess(b);
			double production = mp.network(b);
			printer.printRecord(mode, binLabel(p, b), binStart(p, b), binTime(p, b), kmh(mc), kmh(excess),
				kmh(production), ratio(mc, production), ratio(excess, mc), up * t.distance(b) / 1000.0,
				up * t.travelTime(b) / 3600.0, t.segments(b));
		}
	}

	/** The values of one stats row, in {@link #STATS_HEADER} order. */
	public static List<Object> statsRow(int iteration, MobilityConsumptionAccumulator acc, MobilityProduction mp) {
		MobilityConsumptionParameters p = acc.getParameters();
		double up = p.upscaleFactor();
		BinTotals t = acc.network();
		double mc = up * t.totalConsumption();
		double excess = up * t.totalExcess();
		double production = mp.networkTotal();
		List<Object> row = new ArrayList<>();
		row.add(iteration);
		row.add(p.sampleSize());
		row.add(kmh(mc));
		row.add(kmh(excess));
		row.add(ratio(excess, mc));
		row.add(kmh(production));
		row.add(ratio(mc, production));
		row.add(up * t.totalDistance() / 1000.0);
		row.add(up * t.totalTravelTime() / 3600.0);
		row.add(t.totalSegments());
		row.add(acc.abortedSegments());
		return row;
	}

	/** Writes a segment row; the caller owns the printer. */
	public static void writeSegment(CSVPrinter printer, TraversalSegment s, MobilityConsumptionCalculator calc)
			throws IOException {
		double up = calc.getParameters().upscaleFactor();
		printer.printRecord(s.vehicleId(), s.driverId() == null ? "" : s.driverId(), s.linkId(), s.mode(), s.kind(),
			s.enterTime(), s.leaveTime(), s.distance(), s.freeFlowTime(), s.spaceOccupied(),
			kmh(up * calc.consumption(s)), kmh(up * calc.excess(s)));
	}

	static double kmh(double metreSeconds) {
		return MobilityConsumptionParameters.toKilometreHours(metreSeconds);
	}

	static Object ratio(double numerator, double denominator) {
		return denominator > 0 ? numerator / denominator : "";
	}

	static Object binLabel(MobilityConsumptionParameters p, int bin) {
		return bin == p.outsideBin() ? OUTSIDE : bin;
	}

	static Object binStart(MobilityConsumptionParameters p, int bin) {
		return bin == p.outsideBin() ? "" : p.binStart(bin);
	}

	static Object binTime(MobilityConsumptionParameters p, int bin) {
		return bin == p.outsideBin() ? OUTSIDE : Time.writeTime(p.binStart(bin), Time.TIMEFORMAT_HHMM);
	}
}

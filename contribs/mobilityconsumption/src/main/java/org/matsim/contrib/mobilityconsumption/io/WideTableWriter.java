package org.matsim.contrib.mobilityconsumption.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToDoubleBiFunction;

import org.apache.commons.csv.CSVPrinter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.mobilityconsumption.core.BinTotals;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionAccumulator;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionParameters;
import org.matsim.contrib.mobilityconsumption.core.MobilityProduction;
import org.matsim.core.utils.misc.Time;

/**
 * Writes one row per link and one column per time bin, the layout SimWrapper's link plugin animates with its
 * time slider (first column the link id, the remaining column names the bin start times as HH:MM).
 */
public final class WideTableWriter {

	/** A per-link, per-bin value in reported units. */
	public interface Value extends ToDoubleBiFunction<LinkBin, Integer> {
	}

	/** What a value function sees for one link. */
	public record LinkBin(Id<Link> linkId, BinTotals totals, MobilityProduction production, double upscale) {

		public double consumptionKmh(int bin) {
			return MobilityConsumptionParameters.toKilometreHours(upscale * totals.consumption(bin));
		}

		public double excessKmh(int bin) {
			return MobilityConsumptionParameters.toKilometreHours(upscale * totals.excess(bin));
		}

		public double utilization(int bin) {
			double mp = production.link(linkId, bin);
			return mp > 0 ? upscale * totals.consumption(bin) / mp : 0;
		}

		public double excessRatio(int bin) {
			double mc = totals.consumption(bin);
			return mc > 0 ? totals.excess(bin) / mc : 0;
		}
	}

	public static final Value CONSUMPTION = (lb, bin) -> lb.consumptionKmh(bin);
	public static final Value EXCESS = (lb, bin) -> lb.excessKmh(bin);
	public static final Value UTILIZATION = (lb, bin) -> lb.utilization(bin);
	public static final Value EXCESS_RATIO = (lb, bin) -> lb.excessRatio(bin);

	private final MobilityConsumptionWriter writer;

	public WideTableWriter(MobilityConsumptionWriter writer) {
		this.writer = writer;
	}

	/** Column names: link_id followed by the regular bins' start times. */
	public static List<String> header(MobilityConsumptionParameters p) {
		List<String> header = new ArrayList<>();
		header.add("link_id");
		for (int b = 0; b < p.binCount(); b++) {
			header.add(Time.writeTime(p.binStart(b), Time.TIMEFORMAT_HHMM));
		}
		return header;
	}

	/**
	 * Writes one row for every link known to the production (the whole network) plus any link the accumulator saw
	 * that the network lacks. Links without traffic get zeros: SimWrapper's link plugin needs a value for every
	 * network link, otherwise the layer is not drawn.
	 */
	public void write(String path, MobilityConsumptionAccumulator acc, MobilityProduction mp, Value value) {
		MobilityConsumptionParameters p = acc.getParameters();
		Set<Id<Link>> linkIds = new TreeSet<>(mp.perLink().keySet());
		linkIds.addAll(acc.links().keySet());
		BinTotals empty = new BinTotals(p.binCount() + 1);
		try (CSVPrinter printer = writer.open(path)) {
			printer.printRecord(header(p));
			for (Id<Link> linkId : linkIds) {
				BinTotals totals = acc.links().getOrDefault(linkId, empty);
				LinkBin lb = new LinkBin(linkId, totals, mp, p.upscaleFactor());
				List<Object> row = new ArrayList<>(p.binCount() + 1);
				row.add(linkId);
				for (int b = 0; b < p.binCount(); b++) {
					row.add(value.applyAsDouble(lb, b));
				}
				printer.printRecord(row);
			}
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}
}

package org.matsim.contrib.mobilityconsumption.analysis;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.CommandSpec;
import org.matsim.application.MATSimAppCommand;
import org.matsim.application.options.InputOptions;
import org.matsim.application.options.OutputOptions;
import org.matsim.application.options.SampleOptions;
import org.matsim.application.options.ShpOptions;
import org.matsim.contrib.mobilityconsumption.core.CollectorSettings;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionAccumulator;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionParameters;
import org.matsim.contrib.mobilityconsumption.core.MobilityProduction;
import org.matsim.contrib.mobilityconsumption.core.TraversalSegmentCollector;
import org.matsim.contrib.mobilityconsumption.core.VehicleLengthSource;
import org.matsim.contrib.mobilityconsumption.io.GridRasterWriter;
import org.matsim.contrib.mobilityconsumption.io.MobilityConsumptionWriter;
import org.matsim.contrib.mobilityconsumption.io.WideTableWriter;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scenario.ProjectionUtils;
import org.matsim.vehicles.MatsimVehicleReader;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

import picocli.CommandLine;

/**
 * Computes mobility consumption from an events file and a network, after the fact. Produces the same tables as
 * the in-run listener plus wide per-bin tables for SimWrapper's animated link map and an optional grid raster.
 */
@CommandLine.Command(name = "mobility-consumption",
	description = "Computes mobility consumption (km·h of road space-time) per link and time bin from events.")
@CommandSpec(requireEvents = true, requireNetwork = true, group = "mobilityconsumption",
	produces = {MobilityConsumptionAnalysis.LINKS_DAILY, MobilityConsumptionAnalysis.LINKS_BINS,
		MobilityConsumptionAnalysis.NETWORK_BINS, MobilityConsumptionAnalysis.STATS,
		MobilityConsumptionAnalysis.UTILIZATION_WIDE, MobilityConsumptionAnalysis.EXCESS_RATIO_WIDE,
		MobilityConsumptionAnalysis.CONSUMPTION_WIDE, MobilityConsumptionAnalysis.GRID,
		MobilityConsumptionAnalysis.TILES})
public class MobilityConsumptionAnalysis implements MATSimAppCommand {

	public static final String LINKS_DAILY = "mc_links_daily.csv";
	public static final String LINKS_BINS = "mc_links_bins.csv";
	public static final String NETWORK_BINS = "mc_network_bins.csv";
	public static final String STATS = "mc_stats.csv";
	public static final String UTILIZATION_WIDE = "mc_links_utilization_wide.csv";
	public static final String EXCESS_RATIO_WIDE = "mc_links_excess_ratio_wide.csv";
	public static final String CONSUMPTION_WIDE = "mc_links_mc_wide.csv";
	public static final String GRID = "mc_grid_bins.avro";
	public static final String TILES = "mc_tiles.csv";

	private static final Logger log = LogManager.getLogger(MobilityConsumptionAnalysis.class);

	@CommandLine.Mixin
	private final InputOptions input = InputOptions.ofCommand(MobilityConsumptionAnalysis.class);
	@CommandLine.Mixin
	private final OutputOptions output = OutputOptions.ofCommand(MobilityConsumptionAnalysis.class);
	@CommandLine.Mixin
	private SampleOptions sample;
	@CommandLine.Mixin
	private ShpOptions shp;

	@CommandLine.Option(names = "--vehicle-length", description = "Average vehicle length in m.", defaultValue = "4.87")
	private double vehicleLength;
	@CommandLine.Option(names = "--vehicle-spacing", description = "Average bumper-to-bumper spacing in m.", defaultValue = "6.25")
	private double vehicleSpacing;
	@CommandLine.Option(names = "--reaction-time", description = "Reaction time tau in s.", defaultValue = "1.23")
	private double reactionTime;
	@CommandLine.Option(names = "--time-bin-size", description = "Bin width in s.", defaultValue = "900")
	private double timeBinSize;
	@CommandLine.Option(names = "--analysis-start", description = "Window start in s.", defaultValue = "0")
	private double analysisStart;
	@CommandLine.Option(names = "--analysis-end", description = "Window end in s.", defaultValue = "86400")
	private double analysisEnd;
	@CommandLine.Option(names = "--modes", description = "Network modes to include.", split = ",", defaultValue = TransportMode.car)
	private Set<String> modes;
	@CommandLine.Option(names = "--departure-segments", description = "Count buffer waiting on the departure link.", defaultValue = "true", arity = "1")
	private boolean departureSegments;
	@CommandLine.Option(names = "--arrival-segments", description = "Count the traversal of the arrival link.", defaultValue = "true", arity = "1")
	private boolean arrivalSegments;
	@CommandLine.Option(names = "--exclude-transit", description = "Ignore vehicles driven by transit drivers.", defaultValue = "true", arity = "1")
	private boolean excludeTransit;
	@CommandLine.Option(names = "--vehicle-length-source", description = "fixed or vehicleType.", defaultValue = "fixed")
	private VehicleLengthSource vehicleLengthSource;
	@CommandLine.Option(names = "--vehicles", description = "Vehicles file, needed for vehicleType lengths and maximum velocities.")
	private String vehiclesFile;
	@CommandLine.Option(names = "--time-step-size", description = "Mobsim time step in s.", defaultValue = "1")
	private double timeStepSize;
	@CommandLine.Option(names = "--grid-size", description = "Cell size in m of the animated grid raster; 0 disables it.", defaultValue = "250")
	private double gridSize;
	@CommandLine.Option(names = "--csv-delimiter", description = "Delimiter of the written tables.", defaultValue = ";")
	private String delimiter;

	public static void main(String[] args) {
		new MobilityConsumptionAnalysis().execute(args);
	}

	@Override
	public Integer call() throws Exception {
		Network network = input.getNetwork();
		MobilityConsumptionParameters parameters = new MobilityConsumptionParameters(vehicleLength, vehicleSpacing,
			reactionTime, timeBinSize, analysisStart, analysisEnd, sample.getSample());
		CollectorSettings settings = new CollectorSettings(modes, excludeTransit, departureSegments, arrivalSegments,
			vehicleLengthSource, timeStepSize);

		MobilityConsumptionAccumulator accumulator = new MobilityConsumptionAccumulator(parameters);
		Set<Id<Link>> included = includedLinks(network);
		TraversalSegmentCollector collector = new TraversalSegmentCollector(network, parameters, settings,
			vehicleTypes(), segment -> {
				if (included == null || included.contains(segment.linkId())) {
					accumulator.add(segment);
				}
			}, accumulator::recordAborted);

		EventsManager manager = EventsUtils.createEventsManager();
		manager.addHandler(collector);
		manager.initProcessing();
		EventsUtils.readEvents(manager, input.getEventsPath());
		manager.finishProcessing();
		log.info("Collected {} segments on {} links, {} aborted", accumulator.network().totalSegments(),
			accumulator.links().size(), accumulator.abortedSegments());

		MobilityProduction production = new MobilityProduction(network, parameters, modes);
		MobilityConsumptionWriter writer = new MobilityConsumptionWriter(delimiter);
		writer.writeLinks(output.getPath(LINKS_DAILY).toString(), accumulator, production, network);
		writer.writeLinkBins(output.getPath(LINKS_BINS).toString(), accumulator, production);
		writer.writeNetworkBins(output.getPath(NETWORK_BINS).toString(), accumulator, production);
		writeStats(writer, accumulator, production);
		writeTiles(accumulator, production);

		WideTableWriter wide = new WideTableWriter(writer);
		wide.write(output.getPath(UTILIZATION_WIDE).toString(), accumulator, production, WideTableWriter.UTILIZATION);
		wide.write(output.getPath(EXCESS_RATIO_WIDE).toString(), accumulator, production, WideTableWriter.EXCESS_RATIO);
		wide.write(output.getPath(CONSUMPTION_WIDE).toString(), accumulator, production, WideTableWriter.CONSUMPTION);

		if (gridSize > 0) {
			String crs = ProjectionUtils.getCRS(network);
			if (crs == null) {
				log.warn("Network has no CRS attribute; the grid raster is written without one and may not display.");
				crs = "";
			}
			new GridRasterWriter(network, gridSize, crs).write(output.getPath(GRID).toString(), accumulator);
		}
		return 0;
	}

	private void writeStats(MobilityConsumptionWriter writer, MobilityConsumptionAccumulator acc, MobilityProduction mp) {
		try (CSVPrinter printer = writer.open(output.getPath(STATS).toString())) {
			printer.printRecord(MobilityConsumptionWriter.STATS_HEADER);
			List<Object> row = MobilityConsumptionWriter.statsRow(-1, acc, mp);
			row.set(0, "final");
			printer.printRecord(row);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Label, value and icon rows for SimWrapper's tile row. */
	private void writeTiles(MobilityConsumptionAccumulator acc, MobilityProduction mp) {
		List<Object> row = MobilityConsumptionWriter.statsRow(0, acc, mp);
		String delimiterOverride = ",";
		try (CSVPrinter printer = new MobilityConsumptionWriter(delimiterOverride).open(output.getPath(TILES).toString())) {
			printer.printRecord("Mobility consumption [km·h]", format(row.get(2)), "road");
			printer.printRecord("Excess consumption [km·h]", format(row.get(3)), "clock");
			printer.printRecord("Excess ratio", format(row.get(4)), "percent");
			printer.printRecord("Mobility production [km·h]", format(row.get(5)), "layer-group");
			printer.printRecord("Consumption / production", format(row.get(6)), "gauge-high");
			printer.printRecord("Vehicle-km", format(row.get(7)), "car");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	static String format(Object value) {
		if (value instanceof Number n) {
			double d = n.doubleValue();
			return Math.abs(d) >= 100 ? String.format(java.util.Locale.US, "%,.0f", d)
				: String.format(java.util.Locale.US, "%.4f", d);
		}
		return String.valueOf(value);
	}

	/** Links inside the shape when one is given; null means all links. */
	private Set<Id<Link>> includedLinks(Network network) {
		if (shp == null || !shp.isDefined()) {
			return null;
		}
		ShpOptions.Index index = createIndex(ProjectionUtils.getCRS(network));
		Set<Id<Link>> included = new HashSet<>();
		for (Link link : network.getLinks().values()) {
			if (index.contains(link.getCoord())) {
				included.add(link.getId());
			}
		}
		log.info("Shape filter keeps {} of {} links", included.size(), network.getLinks().size());
		return included;
	}

	/** Transforms the shape into the network's CRS when that is a known code; otherwise assumes they agree. */
	private ShpOptions.Index createIndex(String networkCrs) {
		if (networkCrs != null) {
			try {
				return shp.createIndex(networkCrs, "_");
			} catch (IllegalArgumentException e) {
				log.warn("Network CRS '{}' is not a known code; assuming the shape uses the same coordinates.", networkCrs);
			}
		}
		return shp.createIndex("_");
	}

	private Function<Id<Vehicle>, VehicleType> vehicleTypes() {
		if (vehiclesFile == null) {
			return id -> null;
		}
		Vehicles vehicles = VehicleUtils.createVehiclesContainer();
		new MatsimVehicleReader(vehicles).readFile(vehiclesFile);
		return id -> {
			Vehicle v = vehicles.getVehicles().get(id);
			return v == null ? null : v.getType();
		};
	}
}

package org.matsim.contrib.mobilityconsumption.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.contrib.mobilityconsumption.MobilityConsumptionConfigGroup;
import org.matsim.contrib.mobilityconsumption.RunMobilityConsumption;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControllerConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.gis.GeoFileWriter;
import org.matsim.core.utils.gis.PolygonFeatureFactory;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.api.core.v01.Coord;
import org.matsim.examples.ExamplesUtils;
import org.matsim.testcases.MatsimTestUtils;

class MobilityConsumptionAnalysisTest {

	@RegisterExtension
	private final MatsimTestUtils utils = new MatsimTestUtils();

	static List<CSVRecord> read(Path path) throws IOException {
		try (CSVParser parser = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).get()
			.parse(IOUtils.getBufferedReader(path.toString()))) {
			return parser.getRecords();
		}
	}

	/** The post-hoc command and the in-run listener must agree on the same events. */
	@Test
	void postHocMatchesInRunTotals() throws IOException {
		Config config = ConfigUtils.loadConfig(IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("equil"), "config.xml"),
			new MobilityConsumptionConfigGroup());
		String run = utils.getOutputDirectory() + "run/";
		config.controller().setOutputDirectory(run);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controller().setLastIteration(0);
		config.controller().setCompressionType(ControllerConfigGroup.CompressionType.gzip);
		config.controller().setCreateGraphs(false);
		config.controller().setWriteEventsInterval(1);
		config.qsim().setFlowCapFactor(0.1);
		config.qsim().setStorageCapFactor(0.1);
		ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class).setWriteInterval(1);
		RunMobilityConsumption.prepare(config).run();

		String out = utils.getOutputDirectory() + "analysis/";
		new MobilityConsumptionAnalysis().execute(
			"--events", run + "output_events.xml.gz",
			"--network", run + "output_network.xml.gz",
			"--vehicles", run + "output_vehicles.xml.gz",
			"--sample-size", "0.1",
			"--grid-size", "500",
			"--output-mc-links-daily", out + "mc_links_daily.csv",
			"--output-mc-links-bins", out + "mc_links_bins.csv",
			"--output-mc-network-bins", out + "mc_network_bins.csv",
			"--output-mc-stats", out + "mc_stats.csv",
			"--output-mc-links-utilization-wide", out + "mc_links_utilization_wide.csv",
			"--output-mc-links-excess-ratio-wide", out + "mc_links_excess_ratio_wide.csv",
			"--output-mc-links-mc-wide", out + "mc_links_mc_wide.csv",
			"--output-mc-grid-bins", out + "mc_grid_bins.avro",
			"--output-mc-tiles", out + "mc_tiles.csv");

		List<String> tiles = Files.readAllLines(Path.of(out, "mc_tiles.csv"));
		assertThat(tiles).hasSize(6);
		assertThat(tiles.get(0)).startsWith("Mobility consumption [km·h],");
		assertThat(tiles.get(2)).matches("Excess ratio,0\\.\\d{4},percent");

		CSVRecord inRun = read(Path.of(run, "output_mobilityConsumption_stats.csv")).get(0);
		CSVRecord postHoc = read(Path.of(out, "mc_stats.csv")).get(0);
		assertThat(postHoc.get("iteration")).isEqualTo("final");
		for (String column : List.of("mc_kmh", "mc_excess_kmh", "mp_kmh", "vehicle_km", "vehicle_h")) {
			assertThat(Double.parseDouble(postHoc.get(column)))
				.as(column).isCloseTo(Double.parseDouble(inRun.get(column)), within(1e-6));
		}
		assertThat(postHoc.get("segments")).isEqualTo(inRun.get("segments"));
		// Every car in equil carries its driver: passenger-km equals vehicle-km, and the space-time per passenger-km
		// is lambda / v + tau in metre-seconds per km, a few thousand at urban speeds.
		assertThat(Double.parseDouble(postHoc.get("passenger_km")))
			.isCloseTo(Double.parseDouble(postHoc.get("vehicle_km")), within(1e-6));
		assertThat(Double.parseDouble(postHoc.get("mc_m_s_per_passenger_km"))).isBetween(1000.0, 20000.0);

		List<CSVRecord> inRunLinks = read(Path.of(run, "output_mobilityConsumption_links.csv.gz"));
		List<CSVRecord> postHocLinks = read(Path.of(out, "mc_links_daily.csv"));
		assertThat(postHocLinks).hasSameSizeAs(inRunLinks);

		List<String> wide = Files.readAllLines(Path.of(out, "mc_links_utilization_wide.csv"));
		assertThat(wide.get(0).split(";")).hasSize(97).startsWith("link_id", "00:00", "00:15").endsWith("23:45");
		assertThat(wide).hasSize(24); // one row per network link (23 in equil), even without traffic
		assertThat(Path.of(out, "mc_links_excess_ratio_wide.csv")).exists();
		assertThat(Path.of(out, "mc_links_mc_wide.csv")).exists();
		assertThat(Path.of(out, "mc_grid_bins.avro")).exists();
	}

	@Test
	void otherSettingsAndNoGrid() throws IOException {
		Config config = ConfigUtils.loadConfig(IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("equil"), "config.xml"));
		String run = utils.getOutputDirectory() + "run/";
		config.controller().setOutputDirectory(run);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controller().setLastIteration(0);
		config.controller().setCompressionType(ControllerConfigGroup.CompressionType.gzip);
		config.controller().setCreateGraphs(false);
		config.controller().setWriteEventsInterval(1);
		config.controller().setDumpDataAtEnd(true);
		new org.matsim.core.controler.Controler(config).run();

		String out = utils.getOutputDirectory() + "analysis/";
		new MobilityConsumptionAnalysis().execute(
			"--events", run + "output_events.xml.gz",
			"--network", run + "output_network.xml.gz",
			"--vehicles", emptyVehiclesFile(),
			"--sample-size", "1",
			"--grid-size", "0",
			"--time-bin-size", "3600",
			"--departure-segments", "false",
			"--arrival-segments", "false",
			"--vehicle-length-source", "vehicleType",
			"--csv-delimiter", ",",
			"--output-mc-links-daily", out + "mc_links_daily.csv",
			"--output-mc-links-bins", out + "mc_links_bins.csv",
			"--output-mc-network-bins", out + "mc_network_bins.csv",
			"--output-mc-stats", out + "mc_stats.csv",
			"--output-mc-links-utilization-wide", out + "mc_links_utilization_wide.csv",
			"--output-mc-links-excess-ratio-wide", out + "mc_links_excess_ratio_wide.csv",
			"--output-mc-links-mc-wide", out + "mc_links_mc_wide.csv",
			"--output-mc-grid-bins", out + "mc_grid_bins.avro");
		assertThat(Path.of(out, "mc_grid_bins.avro")).doesNotExist();
		List<String> wide = Files.readAllLines(Path.of(out, "mc_links_utilization_wide.csv"));
		assertThat(wide.get(0).split(",")).hasSize(25);
		List<String> stats = Files.readAllLines(Path.of(out, "mc_stats.csv"));
		assertThat(stats.get(1)).startsWith("final,1.0,");
		assertThat(Double.parseDouble(stats.get(1).split(",")[2])).isGreaterThan(0);
	}

	private String emptyVehiclesFile() {
		String file = utils.getOutputDirectory() + "no-vehicles.xml";
		org.matsim.vehicles.Vehicles vehicles = org.matsim.vehicles.VehicleUtils.createVehiclesContainer();
		vehicles.addVehicleType(org.matsim.vehicles.VehicleUtils.createDefaultVehicleType()); // a type, no vehicles
		new org.matsim.vehicles.MatsimVehicleWriter(vehicles).writeFile(file);
		return file;
	}

	@Test
	void tileFormatting() {
		assertThat(MobilityConsumptionAnalysis.format(1234.5)).isEqualTo("1,235");
		assertThat(MobilityConsumptionAnalysis.format(0.123456)).isEqualTo("0.1235");
		assertThat(MobilityConsumptionAnalysis.format("")).isEmpty();
	}

	/** A shape restricts the accounted links; the west half of equil loses the links east of x = -5000. */
	@Test
	void shapeFilterKeepsOnlyLinksInsideThePolygon() throws IOException {
		Config config = ConfigUtils.loadConfig(IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("equil"), "config.xml"));
		String run = utils.getOutputDirectory() + "run/";
		config.controller().setOutputDirectory(run);
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controller().setLastIteration(0);
		config.controller().setCompressionType(ControllerConfigGroup.CompressionType.gzip);
		config.controller().setCreateGraphs(false);
		config.controller().setWriteEventsInterval(1);
		config.global().setCoordinateSystem("EPSG:25832"); // a resolvable CRS: the shape is transformed into it
		new org.matsim.core.controler.Controler(config).run();

		String shape = utils.getOutputDirectory() + "west.shp";
		PolygonFeatureFactory factory = new PolygonFeatureFactory.Builder()
			.setCrs(MGC.getCRS("EPSG:25832")).setName("west").create();
		GeoFileWriter.writeGeometries(List.of(factory.createPolygon(new Coord[]{
			new Coord(-21000, -11000), new Coord(-5000, -11000), new Coord(-5000, 7000), new Coord(-21000, 7000),
			new Coord(-21000, -11000)})), shape);

		String all = utils.getOutputDirectory() + "all/";
		String west = utils.getOutputDirectory() + "west/";
		String noCrs = utils.getOutputDirectory() + "nocrs/";
		for (String dir : List.of(all, west, noCrs)) {
			// The shipped network carries no CRS attribute: the shape is then assumed to share its coordinates.
			String network = dir.equals(noCrs)
				? IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("equil"), "network.xml").toString()
				: run + "output_network.xml.gz";
			List<String> args = new java.util.ArrayList<>(List.of(
				"--events", run + "output_events.xml.gz",
				"--network", network,
				"--sample-size", "1", "--grid-size", "0",
				"--output-mc-links-daily", dir + "mc_links_daily.csv",
				"--output-mc-links-bins", dir + "mc_links_bins.csv",
				"--output-mc-network-bins", dir + "mc_network_bins.csv",
				"--output-mc-stats", dir + "mc_stats.csv",
				"--output-mc-links-utilization-wide", dir + "mc_links_utilization_wide.csv",
				"--output-mc-links-excess-ratio-wide", dir + "mc_links_excess_ratio_wide.csv",
				"--output-mc-links-mc-wide", dir + "mc_links_mc_wide.csv",
				"--output-mc-grid-bins", dir + "mc_grid_bins.avro",
				"--output-mc-tiles", dir + "mc_tiles.csv"));
			if (!dir.equals(all)) {
				args.add("--shp");
				args.add(shape);
				args.add("--shp-crs");
				args.add("EPSG:25832");
			}
			new MobilityConsumptionAnalysis().execute(args.toArray(new String[0]));
		}
		List<CSVRecord> allLinks = read(Path.of(all, "mc_links_daily.csv"));
		List<CSVRecord> westLinks = read(Path.of(west, "mc_links_daily.csv"));
		assertThat(westLinks).isNotEmpty();
		assertThat(westLinks.size()).isLessThan(allLinks.size());
		// Links 1 to 10 fan out west of x = -5000, 22 and 23 close the loop along the south and west edges.
		assertThat(westLinks).allSatisfy(r -> assertThat(Integer.parseInt(r.get("link_id")))
			.isIn(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 22, 23));
		assertThat(allLinks).anySatisfy(r -> assertThat(r.get("link_id")).isEqualTo("20"));
		assertThat(read(Path.of(noCrs, "mc_links_daily.csv"))).hasSameSizeAs(westLinks);

		// The dashboard CLI also works on a run without the config module and without the stats file.
		org.matsim.contrib.mobilityconsumption.dashboard.CreateMobilityConsumptionDashboard.main(
			new String[]{"--sample-size", "1", run});
		String yaml = Files.readString(Path.of(run, "dashboard-1.yaml"));
		assertThat(yaml).contains("mc_links_utilization_wide.csv").doesNotContain("Consumption over iterations");
	}
}

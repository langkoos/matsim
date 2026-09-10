package org.matsim.contrib.mobilityconsumption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControllerConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.testcases.MatsimTestUtils;

/** Runs the equil scenario for two iterations and checks the outputs against each other. */
class RunMobilityConsumptionTest {

	@RegisterExtension
	private final MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	void outputsAreWrittenAndConsistent() throws IOException {
		Config config = ConfigUtils.loadConfig(
			IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("equil"), "config.xml"),
			new MobilityConsumptionConfigGroup());
		config.controller().setOutputDirectory(utils.getOutputDirectory());
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controller().setLastIteration(1);
		config.controller().setCompressionType(ControllerConfigGroup.CompressionType.gzip);
		config.controller().setCreateGraphs(false);
		config.controller().setWriteEventsInterval(1);
		config.qsim().setFlowCapFactor(0.1);
		config.qsim().setStorageCapFactor(0.1);
		MobilityConsumptionConfigGroup group = ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class);
		group.setWriteInterval(1);
		group.setWriteSegments(true);

		Controler controler = RunMobilityConsumption.prepare(config);
		controler.run();

		Path out = Path.of(utils.getOutputDirectory());
		assertThat(out.resolve("ITERS/it.1/1.mobilityConsumption_links.csv.gz")).exists();
		assertThat(out.resolve("ITERS/it.1/1.mobilityConsumption_links_bins.csv.gz")).exists();
		assertThat(out.resolve("ITERS/it.1/1.mobilityConsumption_network_bins.csv")).exists();
		assertThat(out.resolve("ITERS/it.1/1.mobilityConsumption_segments.csv.gz")).exists();
		assertThat(out.resolve("output_mobilityConsumption_links.csv.gz")).exists();
		assertThat(out.resolve("output_mobilityConsumption_links_bins.csv.gz")).exists();
		assertThat(out.resolve("output_mobilityConsumption_network_bins.csv")).exists();
		assertThat(out.resolve("output_mobilityConsumption_segments.csv.gz")).exists();
		assertThat(out.resolve("output_mobilityConsumption_stats.csv")).exists();

		List<CSVRecord> stats = read(out.resolve("mobilityConsumption_stats.csv"));
		assertThat(stats).hasSize(2);
		CSVRecord last = stats.get(1);
		assertThat(last.get("iteration")).isEqualTo("1");
		assertThat(Double.parseDouble(last.get("sample_size"))).isEqualTo(0.1);
		double mc = Double.parseDouble(last.get("mc_kmh"));
		double excess = Double.parseDouble(last.get("mc_excess_kmh"));
		double mp = Double.parseDouble(last.get("mp_kmh"));
		long segments = Long.parseLong(last.get("segments"));
		assertThat(mc).isGreaterThan(0);
		assertThat(excess).isBetween(0.0, mc);
		assertThat(Double.parseDouble(last.get("mc_mp_ratio"))).isCloseTo(mc / mp, within(1e-9));
		assertThat(Long.parseLong(last.get("aborted_segments"))).isEqualTo(0);

		// Links table sums to the network total; every ratio is well formed.
		List<CSVRecord> links = read(out.resolve("output_mobilityConsumption_links.csv.gz"));
		double linkSum = links.stream().mapToDouble(r -> Double.parseDouble(r.get("mc_kmh"))).sum();
		assertThat(linkSum).isCloseTo(mc, within(1e-6));
		assertThat(links.stream().mapToLong(r -> Long.parseLong(r.get("segments"))).sum()).isEqualTo(segments);
		for (CSVRecord r : links) {
			double u = Double.parseDouble(r.get("utilization"));
			assertThat(u).isGreaterThan(0);
			double e = Double.parseDouble(r.get("excess_ratio"));
			assertThat(e).isBetween(0.0, 1.0);
		}

		// Bins sum to the links.
		List<CSVRecord> bins = read(out.resolve("output_mobilityConsumption_links_bins.csv.gz"));
		double binSum = bins.stream().mapToDouble(r -> Double.parseDouble(r.get("mc_kmh"))).sum();
		assertThat(binSum).isCloseTo(mc, within(1e-6));
		assertThat(bins).anySatisfy(r -> assertThat(r.get("time")).matches("\\d\\d:\\d\\d"));

		// The network-bins 'all' rows sum to the total too, and modes sum to 'all'.
		List<CSVRecord> net = read(out.resolve("output_mobilityConsumption_network_bins.csv"));
		double all = net.stream().filter(r -> r.get("mode").equals("all"))
			.mapToDouble(r -> Double.parseDouble(r.get("mc_kmh"))).sum();
		double modes = net.stream().filter(r -> !r.get("mode").equals("all"))
			.mapToDouble(r -> Double.parseDouble(r.get("mc_kmh"))).sum();
		assertThat(all).isCloseTo(mc, within(1e-6));
		assertThat(modes).isCloseTo(all, within(1e-6));
		assertThat(net).allSatisfy(r -> assertThat(r.get("mode")).isIn("all", "car"));

		// One segment row per counted segment, scaled the same way.
		List<CSVRecord> segs = read(out.resolve("output_mobilityConsumption_segments.csv.gz"));
		assertThat(segs).hasSize((int) segments);
		double segSum = segs.stream().mapToDouble(r -> Double.parseDouble(r.get("mc_kmh"))).sum();
		assertThat(segSum).isCloseTo(mc, within(1e-6));
		assertThat(segs).allSatisfy(r -> assertThat(r.get("driver_id")).isNotEmpty());
	}

	static List<CSVRecord> read(Path path) throws IOException {
		try (CSVParser parser = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).get()
			.parse(IOUtils.getBufferedReader(path.toString()))) {
			return parser.getRecords();
		}
	}
}

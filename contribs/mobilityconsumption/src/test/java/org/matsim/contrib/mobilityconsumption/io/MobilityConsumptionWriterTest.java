package org.matsim.contrib.mobilityconsumption.io;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.mobilityconsumption.core.ConsumptionSource;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionAccumulator;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionParameters;
import org.matsim.contrib.mobilityconsumption.core.MobilityProduction;
import org.matsim.contrib.mobilityconsumption.core.SegmentKind;
import org.matsim.contrib.mobilityconsumption.core.TraversalSegment;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vehicles.Vehicle;

class MobilityConsumptionWriterTest {

	@RegisterExtension
	private final MatsimTestUtils utils = new MatsimTestUtils();

	private static final MobilityConsumptionParameters P =
		new MobilityConsumptionParameters(4.87, 6.25, 1.23, 900, 0, 1800, 0.5);

	private static Network network() {
		Network net = NetworkUtils.createNetwork();
		Node a = NetworkUtils.createAndAddNode(net, Id.createNodeId("a"), new Coord(0, 0));
		Node b = NetworkUtils.createAndAddNode(net, Id.createNodeId("b"), new Coord(1000, 0));
		NetworkUtils.createAndAddLink(net, Id.createLinkId("ab"), a, b, 1000, 10, 1800, 2);
		return net;
	}

	private static TraversalSegment seg(String link, double enter, double leave, double dist, double ff) {
		return new TraversalSegment(Id.createLinkId(link), Id.create("v", Vehicle.class), null, "car",
			SegmentKind.FULL, enter, leave, dist, ff, P.lambda());
	}

	private static List<CSVRecord> read(String path) throws IOException {
		try (CSVParser parser = CSVFormat.DEFAULT.builder().setDelimiter(';').setHeader().setSkipHeaderRecord(true).get()
			.parse(IOUtils.getBufferedReader(path))) {
			return parser.getRecords();
		}
	}

	@Test
	void writesScaledTablesWithOutsideBinAndUnknownLink() throws IOException {
		MobilityConsumptionAccumulator acc = new MobilityConsumptionAccumulator(P);
		acc.add(seg("ab", 0, 100, 1000, 100));
		acc.add(seg("ab", 2000, 2100, 1000, 100)); // after the window: outside bin
		acc.add(seg("zz", 0, 50, 500, 50)); // link not in the network
		acc.add(new TraversalSegment(Id.createLinkId("ab"), Id.create("v", Vehicle.class), null, "car",
			SegmentKind.DEPARTURE, 5000, 5000, 0, 0, P.lambda())); // zero consumption outside the window
		Network net = network();
		MobilityProduction mp = new MobilityProduction(net, P);
		MobilityConsumptionWriter w = new MobilityConsumptionWriter(";");
		String dir = utils.getOutputDirectory();

		w.writeLinks(dir + "links.csv", acc, mp, net);
		List<CSVRecord> links = read(dir + "links.csv");
		assertThat(links).hasSize(2);
		CSVRecord ab = links.get(0);
		assertThat(ab.get("link_id")).isEqualTo("ab");
		assertThat(ab.get("lanes")).isEqualTo("2.0");
		double expectedMc = 2 * (acc.getCalculator().consumption(seg("ab", 0, 100, 1000, 100)) * 2) / 3.6e6;
		assertThat(Double.parseDouble(ab.get("mc_kmh"))).isEqualTo(expectedMc);
		assertThat(Double.parseDouble(ab.get("mp_kmh"))).isEqualTo(1000 * 2 * 1800 / 3.6e6);
		assertThat(ab.get("segments")).isEqualTo("3");
		CSVRecord zz = links.get(1);
		assertThat(zz.get("length_m")).isEmpty();
		assertThat(zz.get("utilization")).isEmpty(); // no production known for the link

		w.writeLinkBins(dir + "bins.csv.gz", acc, mp);
		List<CSVRecord> bins = read(dir + "bins.csv.gz");
		assertThat(bins).extracting(r -> r.get("link_id") + ":" + r.get("bin"))
			.containsExactly("ab:0", "ab:outside", "zz:0");
		assertThat(bins.get(1).get("time")).isEqualTo(MobilityConsumptionWriter.OUTSIDE);
		assertThat(bins.get(1).get("bin_start")).isEmpty();
		assertThat(bins.get(0).get("time")).isEqualTo("00:00");

		w.writeNetworkBins(dir + "net.csv", acc, mp);
		List<CSVRecord> net2 = read(dir + "net.csv");
		// all + car, each with bins 0, 1 (empty regular bins are kept) and the non-empty outside bin.
		assertThat(net2).hasSize(6);
		assertThat(net2.get(1).get("mc_kmh")).isEqualTo("0.0");
		assertThat(net2.get(1).get("excess_ratio")).isEmpty();

		List<Object> stats = MobilityConsumptionWriter.statsRow(3, acc, mp);
		assertThat(stats).hasSize(MobilityConsumptionWriter.STATS_HEADER.size());
		assertThat(stats.get(0)).isEqualTo(3);
		assertThat(stats.get(1)).isEqualTo(0.5);
	}

	@Test
	void appendsAndWritesSegments() throws IOException {
		MobilityConsumptionWriter w = new MobilityConsumptionWriter(",");
		String file = utils.getOutputDirectory() + "segments.csv";
		try (CSVPrinter p = w.open(file)) {
			p.printRecord(MobilityConsumptionWriter.SEGMENT_HEADER);
			MobilityConsumptionWriter.writeSegment(p, seg("ab", 0, 100, 1000, 100),
				new MobilityConsumptionAccumulator(P).getCalculator());
		}
		try (CSVPrinter p = w.openAppend(file)) {
			MobilityConsumptionWriter.writeSegment(p, seg("ab", 100, 300, 1000, 100),
				new MobilityConsumptionAccumulator(P).getCalculator());
		}
		List<String> lines = Files.readAllLines(Path.of(file));
		assertThat(lines).hasSize(3);
		assertThat(lines.get(1)).startsWith("v,,ab,car,FULL,0.0,100.0,1000.0,100.0,11.12");
		assertThat(lines.get(2)).contains(",FULL,100.0,300.0,");
		assertThat(ConsumptionSource.values()).contains(ConsumptionSource.FLOW);
	}
}

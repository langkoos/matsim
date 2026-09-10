package org.matsim.contrib.mobilityconsumption.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionAccumulator;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionParameters;
import org.matsim.contrib.mobilityconsumption.core.MobilityProduction;
import org.matsim.contrib.mobilityconsumption.core.SegmentKind;
import org.matsim.contrib.mobilityconsumption.core.TraversalSegment;
import org.matsim.core.network.NetworkUtils;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vehicles.Vehicle;

class WideTableWriterTest {

	@RegisterExtension
	private final MatsimTestUtils utils = new MatsimTestUtils();

	private static final MobilityConsumptionParameters P =
		new MobilityConsumptionParameters(4.87, 6.25, 1.23, 900, 0, 2700, 0.1);

	static Network network() {
		Network net = NetworkUtils.createNetwork();
		Node a = NetworkUtils.createAndAddNode(net, Id.createNodeId("a"), new Coord(0, 0));
		Node b = NetworkUtils.createAndAddNode(net, Id.createNodeId("b"), new Coord(1000, 0));
		NetworkUtils.createAndAddLink(net, Id.createLinkId("ab"), a, b, 1000, 10, 1800, 1);
		return net;
	}

	@Test
	void oneColumnPerBinAndValuesScaled() throws IOException {
		MobilityConsumptionAccumulator acc = new MobilityConsumptionAccumulator(P);
		// 200 s on the link at 1000 m, free flow 100 s: entirely in bin 1.
		acc.add(new TraversalSegment(Id.createLinkId("ab"), Id.create("v", Vehicle.class), null, "car",
			SegmentKind.FULL, 1000, 1200, 1000, 100, P.lambda()));
		acc.add(new TraversalSegment(Id.createLinkId("zz"), Id.create("v", Vehicle.class), null, "car",
			SegmentKind.FULL, 0, 10, 10, 1, P.lambda())); // unknown link: no production, utilization 0
		MobilityProduction mp = new MobilityProduction(network(), P);
		WideTableWriter w = new WideTableWriter(new MobilityConsumptionWriter(";"));
		String dir = utils.getOutputDirectory();

		assertThat(WideTableWriter.header(P)).containsExactly("link_id", "00:00", "00:15", "00:30");

		w.write(dir + "u.csv", acc, mp, WideTableWriter.UTILIZATION);
		List<String> lines = Files.readAllLines(Path.of(dir + "u.csv"));
		assertThat(lines).hasSize(3);
		assertThat(lines.get(2)).isEqualTo("zz;0.0;0.0;0.0");
		String[] cells = lines.get(1).split(";");
		assertThat(cells[0]).isEqualTo("ab");
		double mc = acc.getCalculator().consumption(acc.links().get(Id.createLinkId("ab")) == null ? null
			: new TraversalSegment(Id.createLinkId("ab"), Id.create("v", Vehicle.class), null, "car",
				SegmentKind.FULL, 1000, 1200, 1000, 100, P.lambda()));
		assertThat(Double.parseDouble(cells[1])).isEqualTo(0.0);
		assertThat(Double.parseDouble(cells[2])).isCloseTo(10 * mc / (1000 * 900), within(1e-9));
		assertThat(Double.parseDouble(cells[3])).isEqualTo(0.0);

		w.write(dir + "e.csv", acc, mp, WideTableWriter.EXCESS_RATIO);
		cells = Files.readAllLines(Path.of(dir + "e.csv")).get(1).split(";");
		assertThat(Double.parseDouble(cells[2])).isCloseTo(11.12 * 100 / mc, within(1e-9));

		w.write(dir + "mc.csv", acc, mp, WideTableWriter.CONSUMPTION);
		cells = Files.readAllLines(Path.of(dir + "mc.csv")).get(1).split(";");
		assertThat(Double.parseDouble(cells[2])).isCloseTo(10 * mc / 3.6e6, within(1e-12));
		w.write(dir + "ex.csv", acc, mp, WideTableWriter.EXCESS);
		cells = Files.readAllLines(Path.of(dir + "ex.csv")).get(1).split(";");
		assertThat(Double.parseDouble(cells[2])).isCloseTo(10 * 11.12 * 100 / 3.6e6, within(1e-12));
	}
}

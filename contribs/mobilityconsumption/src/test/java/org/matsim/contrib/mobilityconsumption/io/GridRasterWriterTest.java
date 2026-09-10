package org.matsim.contrib.mobilityconsumption.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.apache.avro.file.DataFileReader;
import org.apache.avro.specific.SpecificDatumReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.application.avro.XYTData;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionAccumulator;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionParameters;
import org.matsim.contrib.mobilityconsumption.core.SegmentKind;
import org.matsim.contrib.mobilityconsumption.core.TraversalSegment;
import org.matsim.core.network.NetworkUtils;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vehicles.Vehicle;

class GridRasterWriterTest {

	@RegisterExtension
	private final MatsimTestUtils utils = new MatsimTestUtils();

	private static final MobilityConsumptionParameters P =
		new MobilityConsumptionParameters(4.87, 6.25, 1.23, 900, 0, 1800, 0.5);

	/** Two links: a 1000 m horizontal one and a 300 m diagonal one, on a 250 m grid. */
	static Network network() {
		Network net = NetworkUtils.createNetwork();
		Node a = NetworkUtils.createAndAddNode(net, Id.createNodeId("a"), new Coord(0, 0));
		Node b = NetworkUtils.createAndAddNode(net, Id.createNodeId("b"), new Coord(1000, 0));
		Node c = NetworkUtils.createAndAddNode(net, Id.createNodeId("c"), new Coord(1200, 300));
		NetworkUtils.createAndAddLink(net, Id.createLinkId("ab"), a, b, 1000, 10, 1800, 1);
		NetworkUtils.createAndAddLink(net, Id.createLinkId("bc"), b, c, 300, 10, 1800, 1);
		return net;
	}

	@Test
	void cellSharesSumToOneAndSpreadAlongTheLink() {
		Network net = network();
		GridRasterWriter w = new GridRasterWriter(net, 250, "EPSG:25832");
		GridRasterWriter.Extent ext = w.extent();
		assertThat(ext.nx()).isEqualTo(5);
		assertThat(ext.ny()).isEqualTo(2);
		Link ab = net.getLinks().get(Id.createLinkId("ab"));
		Map<Integer, Double> shares = w.cellShares(ab, ext);
		assertThat(shares.values().stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, within(1e-9));
		// 1000 m sampled every 125 m: 8 samples across cells x=0..3 (two each), y=0.
		assertThat(shares).hasSize(4);
		assertThat(shares.get(0)).isCloseTo(0.25, within(1e-9));
		assertThatThrownBy(() -> new GridRasterWriter(net, 0, "")).isInstanceOf(IllegalArgumentException.class);
		Node b = net.getNodes().get(Id.createNodeId("b"));
		Link loop = NetworkUtils.createAndAddLink(net, Id.createLinkId("bb"), b, b, 0, 10, 1800, 1);
		assertThat(w.cellShares(loop, ext)).containsExactly(Map.entry(4 * 2 + 0, 1.0));
	}

	@Test
	void rasterSumsMatchTheAccumulator() throws IOException {
		Network net = network();
		MobilityConsumptionAccumulator acc = new MobilityConsumptionAccumulator(P);
		acc.add(new TraversalSegment(Id.createLinkId("ab"), Id.create("v", Vehicle.class), null, "car",
			SegmentKind.FULL, 0, 200, 1000, 100, P.lambda()));
		acc.add(new TraversalSegment(Id.createLinkId("bc"), Id.create("v", Vehicle.class), null, "car",
			SegmentKind.FULL, 1000, 1030, 300, 30, P.lambda()));
		acc.add(new TraversalSegment(Id.createLinkId("nope"), Id.create("v", Vehicle.class), null, "car",
			SegmentKind.FULL, 1000, 1030, 300, 30, P.lambda()));
		String file = utils.getOutputDirectory() + "grid.avro";
		new GridRasterWriter(net, 250, "EPSG:25832").write(file, acc);

		try (DataFileReader<XYTData> reader = new DataFileReader<>(new File(file), new SpecificDatumReader<>(XYTData.class))) {
			XYTData data = reader.next();
			assertThat(data.getCrs().toString()).isEqualTo("EPSG:25832");
			assertThat(data.getTimestamps()).containsExactly(0, 900);
			assertThat(data.getXCoords()).hasSize(5);
			assertThat(data.getYCoords()).hasSize(2);
			List<Float> mc = series(data, GridRasterWriter.CONSUMPTION_KEY);
			assertThat(mc).hasSize(2 * 5 * 2);
			double total = mc.stream().mapToDouble(Float::doubleValue).sum();
			double expected = 2 * (acc.links().get(Id.createLinkId("ab")).totalConsumption()
				+ acc.links().get(Id.createLinkId("bc")).totalConsumption()) / 3.6e6;
			assertThat(total).isCloseTo(expected, within(1e-6));
			// Bin 0 holds only link ab, bin 1 only link bc.
			double bin0 = mc.subList(0, 10).stream().mapToDouble(Float::doubleValue).sum();
			assertThat(bin0).isCloseTo(2 * acc.links().get(Id.createLinkId("ab")).totalConsumption() / 3.6e6, within(1e-6));
			List<Float> excess = series(data, GridRasterWriter.EXCESS_KEY);
			assertThat(excess.stream().mapToDouble(Float::doubleValue).sum())
				.isCloseTo(2 * 11.12 * 100 / 3.6e6, within(1e-6));
		}
	}

	/** Avro returns map keys as Utf8, so look the series up by string value. */
	private static List<Float> series(XYTData data, String key) {
		return data.getData().entrySet().stream().filter(e -> e.getKey().toString().equals(key))
			.map(Map.Entry::getValue).findFirst().orElseThrow();
	}
}

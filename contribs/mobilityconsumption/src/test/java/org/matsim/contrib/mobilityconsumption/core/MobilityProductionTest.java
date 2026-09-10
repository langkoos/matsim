package org.matsim.contrib.mobilityconsumption.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;

class MobilityProductionTest {

	static Network twoLinkNetwork() {
		Network net = NetworkUtils.createNetwork();
		Node a = NetworkUtils.createAndAddNode(net, Id.createNodeId("a"), new Coord(0, 0));
		Node b = NetworkUtils.createAndAddNode(net, Id.createNodeId("b"), new Coord(1000, 0));
		Node c = NetworkUtils.createAndAddNode(net, Id.createNodeId("c"), new Coord(2000, 0));
		NetworkUtils.createAndAddLink(net, Id.createLinkId("ab"), a, b, 1000, 10, 1800, 2);
		NetworkUtils.createAndAddLink(net, Id.createLinkId("bc"), b, c, 500, 10, 900, 1);
		return net;
	}

	@Test
	void productionIsLengthTimesLanesTimesBinLength() {
		MobilityConsumptionParameters p = MobilityConsumptionParameters.DEFAULTS;
		MobilityProduction mp = new MobilityProduction(twoLinkNetwork(), p);
		assertThat(mp.link(Id.createLinkId("ab"), 0)).isEqualTo(1000 * 2 * 900);
		assertThat(mp.link(Id.createLinkId("bc"), 5)).isEqualTo(500 * 1 * 900);
		assertThat(mp.link(Id.createLinkId("ab"), p.outsideBin())).isEqualTo(0);
		assertThat(mp.link(Id.createLinkId("nope"), 0)).isEqualTo(0);
		assertThat(mp.linkTotal(Id.createLinkId("ab"))).isCloseTo(1000 * 2 * 86400, within(1e-6));
		assertThat(mp.networkTotal()).isCloseTo((1000 * 2 + 500) * 86400.0, within(1e-6));
		assertThat(mp.network(3)).isEqualTo((1000 * 2 + 500) * 900.0);
		assertThat(MobilityConsumptionParameters.toKilometreHours(mp.networkTotal())).isCloseTo(2.5 * 24, within(1e-9));
	}

	@Test
	void partialLastBinIsShorter() {
		MobilityConsumptionParameters p = new MobilityConsumptionParameters(4.87, 6.25, 1.23, 1000, 0, 2500, 1.0);
		MobilityProduction mp = new MobilityProduction(twoLinkNetwork(), p);
		Id<Link> ab = Id.createLinkId("ab");
		assertThat(mp.link(ab, 2)).isEqualTo(1000 * 2 * 500);
		assertThat(mp.linkTotal(ab)).isEqualTo(1000 * 2 * 2500);
	}
}

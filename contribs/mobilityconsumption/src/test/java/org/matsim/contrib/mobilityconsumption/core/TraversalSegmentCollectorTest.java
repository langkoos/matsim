package org.matsim.contrib.mobilityconsumption.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleAbortsEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.network.NetworkUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

class TraversalSegmentCollectorTest {

	private static final MobilityConsumptionParameters P = MobilityConsumptionParameters.DEFAULTS;
	private static final Id<Vehicle> V = Id.create("v1", Vehicle.class);
	private static final Id<Person> D = Id.create("p1", Person.class);
	private static final Id<Link> L0 = Id.createLinkId("l0");
	private static final Id<Link> L1 = Id.createLinkId("l1");
	private static final Id<Link> L2 = Id.createLinkId("l2");

	/** Three links of 1000 m at 10 m/s: free-flow 100 s each. */
	static Network network() {
		Network net = NetworkUtils.createNetwork();
		Node a = NetworkUtils.createAndAddNode(net, Id.createNodeId("a"), new Coord(0, 0));
		Node b = NetworkUtils.createAndAddNode(net, Id.createNodeId("b"), new Coord(1000, 0));
		Node c = NetworkUtils.createAndAddNode(net, Id.createNodeId("c"), new Coord(2000, 0));
		Node d = NetworkUtils.createAndAddNode(net, Id.createNodeId("d"), new Coord(3000, 0));
		NetworkUtils.createAndAddLink(net, L0, a, b, 1000, 10, 1800, 1);
		NetworkUtils.createAndAddLink(net, L1, b, c, 1000, 10, 1800, 1);
		NetworkUtils.createAndAddLink(net, L2, c, d, 1000, 10, 1800, 1);
		return net;
	}

	private final List<TraversalSegment> segments = new ArrayList<>();
	private final AtomicInteger aborted = new AtomicInteger();

	private TraversalSegmentCollector collector(CollectorSettings settings) {
		return new TraversalSegmentCollector(network(), P, settings, id -> null, segments::add, aborted::incrementAndGet);
	}

	/** Free-flow leg: departs at 0, waits 0 s, drives l1 in 101 s (100 + node step), arrives on l2 after 100 s. */
	private static void freeFlowLeg(TraversalSegmentCollector c, String mode) {
		c.handleEvent(new VehicleEntersTrafficEvent(0, D, L0, V, mode, 1.0));
		c.handleEvent(new LinkLeaveEvent(1, V, L0));
		c.handleEvent(new LinkEnterEvent(1, V, L1));
		c.handleEvent(new LinkLeaveEvent(102, V, L1));
		c.handleEvent(new LinkEnterEvent(102, V, L2));
		c.handleEvent(new VehicleLeavesTrafficEvent(202, D, L2, V, mode, 1.0));
	}

	@Test
	void freeFlowLegProducesThreeSegmentsWithoutExcess() {
		TraversalSegmentCollector c = collector(CollectorSettings.CAR_DEFAULTS);
		freeFlowLeg(c, "car");
		assertThat(segments).extracting(TraversalSegment::kind)
			.containsExactly(SegmentKind.DEPARTURE, SegmentKind.FULL, SegmentKind.ARRIVAL);
		assertThat(segments).extracting(TraversalSegment::linkId).containsExactly(L0, L1, L2);
		assertThat(segments).extracting(TraversalSegment::distance).containsExactly(0.0, 1000.0, 1000.0);
		assertThat(segments).extracting(TraversalSegment::travelTime).containsExactly(1.0, 101.0, 100.0);
		assertThat(segments).extracting(TraversalSegment::freeFlowTime).containsExactly(0.0, 101.0, 100.0);
		assertThat(segments).allSatisfy(s -> {
			assertThat(s.driverId()).isEqualTo(D);
			assertThat(s.mode()).isEqualTo("car");
			assertThat(s.spaceOccupied()).isCloseTo(11.12, within(1e-9));
		});
		MobilityConsumptionCalculator calc = new MobilityConsumptionCalculator(P);
		assertThat(segments.get(1).travelTime() - segments.get(1).freeFlowTime()).isEqualTo(0);
		assertThat(calc.excess(segments.get(2))).isEqualTo(0);
		assertThat(c.vehiclesInTraffic()).isEqualTo(0);
	}

	@Test
	void congestionShowsUpAsExcess() {
		TraversalSegmentCollector c = collector(CollectorSettings.CAR_DEFAULTS);
		c.handleEvent(new VehicleEntersTrafficEvent(0, D, L0, V, "car", 1.0));
		c.handleEvent(new LinkLeaveEvent(60, V, L0));
		c.handleEvent(new LinkEnterEvent(60, V, L1));
		c.handleEvent(new LinkLeaveEvent(400, V, L1));
		c.handleEvent(new LinkEnterEvent(400, V, L2));
		c.handleEvent(new VehicleLeavesTrafficEvent(500, D, L2, V, "car", 1.0));
		MobilityConsumptionCalculator calc = new MobilityConsumptionCalculator(P);
		assertThat(calc.excess(segments.get(0))).isCloseTo(11.12 * 60, within(1e-9));
		assertThat(calc.excess(segments.get(1))).isCloseTo(11.12 * (340 - 101), within(1e-9));
		assertThat(calc.excess(segments.get(2))).isEqualTo(0);
	}

	@Test
	void departureAndArrivalCanBeSwitchedOff() {
		TraversalSegmentCollector c = collector(new CollectorSettings(Set.of("car"), true, false, false,
			VehicleLengthSource.fixed, 1.0));
		freeFlowLeg(c, "car");
		assertThat(segments).extracting(TraversalSegment::kind).containsExactly(SegmentKind.FULL);
	}

	@Test
	void singleLinkLegIsDepartureOnly() {
		TraversalSegmentCollector c = collector(CollectorSettings.CAR_DEFAULTS);
		c.handleEvent(new VehicleEntersTrafficEvent(10, D, L0, V, "car", 1.0));
		c.handleEvent(new VehicleLeavesTrafficEvent(15, D, L0, V, "car", 1.0));
		assertThat(segments).hasSize(1);
		assertThat(segments.get(0).kind()).isEqualTo(SegmentKind.DEPARTURE);
		assertThat(segments.get(0).travelTime()).isEqualTo(5);
		assertThat(segments.get(0).distance()).isEqualTo(0);
	}

	@Test
	void otherModesAndTransitVehiclesAreIgnored() {
		TraversalSegmentCollector c = collector(CollectorSettings.CAR_DEFAULTS);
		freeFlowLeg(c, "bike");
		assertThat(segments).isEmpty();
		c.handleEvent(new TransitDriverStartsEvent(0, D, V, Id.create("line", TransitLine.class),
			Id.create("route", TransitRoute.class), Id.create("dep", Departure.class)));
		freeFlowLeg(c, "car");
		assertThat(segments).isEmpty();
		c.reset(1);
		freeFlowLeg(c, "car");
		assertThat(segments).hasSize(3);
	}

	@Test
	void abortDropsTheOpenSegmentAndCountsIt() {
		TraversalSegmentCollector c = collector(CollectorSettings.CAR_DEFAULTS);
		c.handleEvent(new VehicleEntersTrafficEvent(0, D, L0, V, "car", 1.0));
		c.handleEvent(new LinkLeaveEvent(1, V, L0));
		c.handleEvent(new LinkEnterEvent(1, V, L1));
		c.handleEvent(new VehicleAbortsEvent(3000, V, L1));
		assertThat(segments).hasSize(1);
		assertThat(aborted.get()).isEqualTo(1);
		assertThat(c.vehiclesInTraffic()).isEqualTo(0);
		// Stray events after the abort do nothing.
		c.handleEvent(new LinkLeaveEvent(3001, V, L1));
		assertThat(segments).hasSize(1);
	}

	@Test
	void vehicleTypeSuppliesLengthAndMaximumVelocity() {
		VehicleType slowTruck = VehicleUtils.createVehicleType(Id.create("truck", VehicleType.class));
		slowTruck.setLength(18.0);
		slowTruck.setMaximumVelocity(5.0);
		CollectorSettings settings = new CollectorSettings(Set.of("car"), true, true, true,
			VehicleLengthSource.vehicleType, 1.0);
		TraversalSegmentCollector c = new TraversalSegmentCollector(network(), P, settings, id -> slowTruck,
			segments::add, aborted::incrementAndGet);
		freeFlowLeg(c, "car");
		assertThat(segments.get(1).spaceOccupied()).isEqualTo(18.0);
		// 1000 m at min(10, 5) m/s = 200 s, plus the node step.
		assertThat(segments.get(1).freeFlowTime()).isEqualTo(201.0);
		assertThat(segments.get(2).freeFlowTime()).isEqualTo(200.0);
	}

	@Test
	void freeFlowTimeIsFlooredToTheTimeStep() {
		TraversalSegmentCollector c = collector(new CollectorSettings(Set.of("car"), true, true, true,
			VehicleLengthSource.fixed, 2.0));
		Link l1 = network().getLinks().get(L1);
		// 100 s exact stays 100; a 1000 m link at 3 m/s is 333.3 s, floored to 332 with 2 s steps.
		assertThat(c.freeFlowTime(l1, 0, Double.POSITIVE_INFINITY)).isEqualTo(100.0);
		assertThat(c.freeFlowTime(l1, 0, 3.0)).isEqualTo(332.0);
	}
}

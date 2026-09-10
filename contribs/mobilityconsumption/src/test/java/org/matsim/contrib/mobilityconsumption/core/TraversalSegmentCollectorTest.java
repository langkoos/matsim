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
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
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

	@Test
	void strayEventsAreIgnored() {
		TraversalSegmentCollector c = collector(CollectorSettings.CAR_DEFAULTS);
		// Leave/enter/abort without a tracked leg.
		c.handleEvent(new LinkLeaveEvent(5, V, L0));
		c.handleEvent(new LinkEnterEvent(5, V, L1));
		c.handleEvent(new VehicleLeavesTrafficEvent(6, D, L1, V, "car", 1.0));
		c.handleEvent(new VehicleAbortsEvent(7, V, L1));
		assertThat(segments).isEmpty();
		assertThat(aborted.get()).isEqualTo(0);
		// A link-leave on a link other than the open one is dropped, as is a leaves-traffic elsewhere.
		c.handleEvent(new VehicleEntersTrafficEvent(10, D, L0, V, "car", 1.0));
		c.handleEvent(new LinkLeaveEvent(11, V, L2));
		c.handleEvent(new LinkEnterEvent(11, V, L1));
		c.handleEvent(new VehicleLeavesTrafficEvent(12, D, L2, V, "car", 1.0));
		assertThat(segments).isEmpty();
		assertThat(c.vehiclesInTraffic()).isEqualTo(0);
		// Old event files carry no network mode: such vehicles are ignored rather than crashing the run.
		c.handleEvent(new VehicleEntersTrafficEvent(18, D, L0, V, null, 1.0));
		c.handleEvent(new LinkLeaveEvent(19, V, L0));
		assertThat(segments).isEmpty();
		// A vehicle switching to an excluded mode forgets its state.
		c.handleEvent(new VehicleEntersTrafficEvent(20, D, L0, V, "car", 1.0));
		c.handleEvent(new VehicleEntersTrafficEvent(21, D, L0, V, "bike", 1.0));
		c.handleEvent(new LinkLeaveEvent(22, V, L0));
		assertThat(segments).isEmpty();
	}

	@Test
	void singleLinkLegWithoutDepartureSegmentsProducesNothing() {
		TraversalSegmentCollector c = collector(new CollectorSettings(Set.of("car"), true, false, true,
			VehicleLengthSource.fixed, 1.0));
		c.handleEvent(new VehicleEntersTrafficEvent(10, D, L0, V, "car", 1.0));
		c.handleEvent(new VehicleLeavesTrafficEvent(15, D, L0, V, "car", 1.0));
		assertThat(segments).isEmpty();
		// Arrival switched off: only the departure and full segments remain.
		TraversalSegmentCollector d = collector(new CollectorSettings(Set.of("car"), true, true, false,
			VehicleLengthSource.fixed, 1.0));
		freeFlowLeg(d, "car");
		assertThat(segments).extracting(TraversalSegment::kind)
			.containsExactly(SegmentKind.DEPARTURE, SegmentKind.FULL);
	}

	@Test
	void transitVehiclesAreKeptWhenNotExcluded() {
		TraversalSegmentCollector c = collector(new CollectorSettings(Set.of("car"), false, true, true,
			VehicleLengthSource.fixed, 1.0));
		c.handleEvent(new TransitDriverStartsEvent(0, D, V, Id.create("line", TransitLine.class),
			Id.create("route", TransitRoute.class), Id.create("dep", Departure.class)));
		freeFlowLeg(c, "car");
		assertThat(segments).hasSize(3);
		assertThat(segments).extracting(TraversalSegment::mode).containsOnly("pt:unknown");
		assertThat(segments).extracting(TraversalSegment::source).containsOnly(ConsumptionSource.TRANSIT);
	}

	@Test
	void unknownVehicleTypeFallsBackToFixedLambda() {
		TraversalSegmentCollector c = collector(new CollectorSettings(Set.of("car"), true, true, true,
			VehicleLengthSource.vehicleType, 1.0));
		freeFlowLeg(c, "car");
		assertThat(segments.get(1).spaceOccupied()).isCloseTo(11.12, within(1e-9));
		assertThat(segments.get(1).freeFlowTime()).isEqualTo(101.0);
	}

	@Test
	void carOccupancyCountsTheDriverAndPassengers() {
		TraversalSegmentCollector c = collector(CollectorSettings.CAR_DEFAULTS);
		Id<Person> passenger = Id.create("p2", Person.class);
		c.handleEvent(new PersonEntersVehicleEvent(0, D, V));
		c.handleEvent(new VehicleEntersTrafficEvent(0, D, L0, V, "car", 1.0));
		c.handleEvent(new LinkLeaveEvent(1, V, L0));
		c.handleEvent(new LinkEnterEvent(1, V, L1));
		c.handleEvent(new PersonEntersVehicleEvent(51, passenger, V)); // boards half-way through the 100 s link
		c.handleEvent(new LinkLeaveEvent(101, V, L1));
		c.handleEvent(new LinkEnterEvent(101, V, L2));
		c.handleEvent(new VehicleLeavesTrafficEvent(201, D, L2, V, "car", 1.0));
		c.handleEvent(new PersonLeavesVehicleEvent(201, passenger, V));
		c.handleEvent(new PersonLeavesVehicleEvent(201, D, V));
		assertThat(segments).extracting(TraversalSegment::occupancy).containsExactly(1.0, 1.5, 2.0);
		assertThat(segments).extracting(TraversalSegment::source).containsOnly(ConsumptionSource.FLOW);
		// A vehicle nobody boarded (freight without a person event) has zero occupancy.
		Id<Vehicle> lorry = Id.create("lorry", Vehicle.class);
		c.handleEvent(new VehicleEntersTrafficEvent(300, D, L0, lorry, "car", 1.0));
		c.handleEvent(new LinkLeaveEvent(301, lorry, L0));
		c.handleEvent(new LinkEnterEvent(301, lorry, L1));
		c.handleEvent(new LinkLeaveEvent(402, lorry, L1));
		assertThat(segments.get(segments.size() - 1).occupancy()).isEqualTo(0.0);
	}

	@Test
	void includedTransitVehiclesCarryPassengersNotTheDriverAndDwellIsNotExcess() {
		VehicleType bus = VehicleUtils.createVehicleType(Id.create("Bus_veh_type", VehicleType.class));
		CollectorSettings settings = new CollectorSettings(Set.of("car"), false, true, true, VehicleLengthSource.fixed, 1.0,
			java.util.Map.of("Bus_veh_type", 18.25));
		TraversalSegmentCollector c = new TraversalSegmentCollector(network(), P, settings, id -> bus, segments::add,
			aborted::incrementAndGet);
		Id<Person> driver = Id.create("pt_driver", Person.class);
		Id<Person> a = Id.create("a", Person.class);
		Id<Person> b = Id.create("b", Person.class);
		c.handleEvent(new TransitDriverStartsEvent(0, driver, V, Id.create("line", TransitLine.class),
			Id.create("route", TransitRoute.class), Id.create("dep", Departure.class)));
		c.handleEvent(new PersonEntersVehicleEvent(0, driver, V)); // the driver does not count
		c.handleEvent(new PersonEntersVehicleEvent(0, a, V));
		c.handleEvent(new PersonEntersVehicleEvent(0, b, V));
		c.handleEvent(new VehicleEntersTrafficEvent(0, driver, L0, V, "car", 1.0));
		c.handleEvent(new LinkLeaveEvent(1, V, L0));
		c.handleEvent(new LinkEnterEvent(1, V, L1));
		// A 30 s stop on l1: arrives, one alights, departs. The link then takes 130 s instead of 100.
		c.handleEvent(new VehicleArrivesAtFacilityEvent(50, V, Id.create("stop", TransitStopFacility.class), 0));
		c.handleEvent(new PersonLeavesVehicleEvent(50, b, V));
		c.handleEvent(new VehicleDepartsAtFacilityEvent(80, V, Id.create("stop", TransitStopFacility.class), 0));
		c.handleEvent(new LinkLeaveEvent(131, V, L1));
		c.handleEvent(new LinkEnterEvent(131, V, L2));
		c.handleEvent(new VehicleLeavesTrafficEvent(231, driver, L2, V, "car", 1.0));

		assertThat(segments).hasSize(3);
		TraversalSegment onL1 = segments.get(1);
		assertThat(onL1.source()).isEqualTo(ConsumptionSource.TRANSIT);
		assertThat(onL1.mode()).isEqualTo("pt:Bus_veh_type");
		assertThat(onL1.spaceOccupied()).isEqualTo(18.25);
		assertThat(onL1.travelTime()).isEqualTo(130.0);
		assertThat(onL1.freeFlowTime()).isEqualTo(101.0 + 30.0); // scheduled dwell is not excess
		assertThat(new MobilityConsumptionCalculator(P).excess(onL1)).isEqualTo(0);
		// two aboard for 49 s, one for 81 s: mean 1.377
		assertThat(onL1.occupancy()).isCloseTo((2 * 49 + 1 * 81) / 130.0, within(1e-9));
		assertThat(segments.get(2).occupancy()).isEqualTo(1.0);
	}

	@Test
	void excludedTransitVehiclesStillDoNotCount() {
		TraversalSegmentCollector c = collector(CollectorSettings.CAR_DEFAULTS);
		c.handleEvent(new TransitDriverStartsEvent(0, D, V, Id.create("line", TransitLine.class),
			Id.create("route", TransitRoute.class), Id.create("dep", Departure.class)));
		freeFlowLeg(c, "car");
		assertThat(segments).isEmpty();
	}

	@Test
	void strayOccupancyAndStopEventsAndZeroDurationSegments() {
		TraversalSegmentCollector c = collector(CollectorSettings.CAR_DEFAULTS);
		// Leaving a vehicle nobody was tracked in, and departing a stop without arriving, are ignored.
		c.handleEvent(new PersonLeavesVehicleEvent(5, D, V));
		c.handleEvent(new VehicleEntersTrafficEvent(10, D, L0, V, "car", 1.0));
		c.handleEvent(new VehicleDepartsAtFacilityEvent(11, V, Id.create("stop", TransitStopFacility.class), 0));
		// Enters and leaves traffic in the same second: a zero-duration departure segment uses the current occupancy.
		c.handleEvent(new VehicleLeavesTrafficEvent(10, D, L0, V, "car", 1.0));
		assertThat(segments).hasSize(1);
		assertThat(segments.get(0).travelTime()).isEqualTo(0);
		assertThat(segments.get(0).occupancy()).isEqualTo(0);
		// A passenger leaving after the leg ended clears the vehicle's occupancy record without error.
		c.handleEvent(new PersonEntersVehicleEvent(20, D, V));
		c.handleEvent(new PersonLeavesVehicleEvent(25, D, V));
		c.handleEvent(new VehicleEntersTrafficEvent(30, D, L0, V, "car", 1.0));
		c.handleEvent(new VehicleLeavesTrafficEvent(30, D, L0, V, "car", 1.0));
		assertThat(segments.get(1).occupancy()).isEqualTo(0);
		// A passenger alighting while the vehicle is still in traffic empties the record but keeps it.
		c.handleEvent(new PersonEntersVehicleEvent(40, D, V));
		c.handleEvent(new VehicleEntersTrafficEvent(40, D, L0, V, "car", 1.0));
		c.handleEvent(new PersonLeavesVehicleEvent(45, D, V));
		c.handleEvent(new LinkLeaveEvent(50, V, L0));
		assertThat(segments.get(2).occupancy()).isCloseTo(0.5, within(1e-9));
		// A stop still open when the link ends counts its dwell up to the link leave.
		c.handleEvent(new LinkEnterEvent(50, V, L1));
		c.handleEvent(new VehicleArrivesAtFacilityEvent(100, V, Id.create("stop", TransitStopFacility.class), 0));
		c.handleEvent(new LinkLeaveEvent(160, V, L1));
		assertThat(segments.get(3).freeFlowTime()).isEqualTo(101.0 + 60.0);
	}
}

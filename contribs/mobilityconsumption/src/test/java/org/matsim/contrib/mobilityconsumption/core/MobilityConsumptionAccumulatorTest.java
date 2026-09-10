package org.matsim.contrib.mobilityconsumption.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.vehicles.Vehicle;

class MobilityConsumptionAccumulatorTest {

	private static final MobilityConsumptionParameters P = MobilityConsumptionParameters.DEFAULTS;

	private static TraversalSegment seg(String link, String mode, double enter, double leave, double dist, double ff) {
		return new TraversalSegment(Id.createLinkId(link), Id.create("v", Vehicle.class), Id.create("p", Person.class),
			mode, SegmentKind.FULL, enter, leave, dist, ff, P.lambda());
	}

	@Test
	void sumsPerLinkPerModeAndNetworkAgree() {
		MobilityConsumptionAccumulator acc = new MobilityConsumptionAccumulator(P);
		acc.add(seg("a", "car", 0, 100, 1000, 100));
		acc.add(seg("a", "car", 800, 1000, 1000, 100));
		acc.add(seg("b", "truck", 100, 300, 2000, 200));
		MobilityConsumptionCalculator calc = acc.getCalculator();
		double expected = calc.consumption(seg("a", "car", 0, 100, 1000, 100))
			+ calc.consumption(seg("a", "car", 800, 1000, 1000, 100))
			+ calc.consumption(seg("b", "truck", 100, 300, 2000, 200));

		assertThat(acc.network().totalConsumption()).isCloseTo(expected, within(1e-9));
		assertThat(acc.links().get(Id.createLinkId("a")).totalConsumption()
			+ acc.links().get(Id.createLinkId("b")).totalConsumption()).isCloseTo(expected, within(1e-9));
		assertThat(acc.modes().get("car").totalConsumption() + acc.modes().get("truck").totalConsumption())
			.isCloseTo(expected, within(1e-9));
		assertThat(acc.network().totalSegments()).isEqualTo(3);
		assertThat(acc.links().get(Id.createLinkId("a")).segments(0)).isEqualTo(2);
		assertThat(acc.links().get(Id.createLinkId("a")).consumption(1)).isGreaterThan(0);
		assertThat(acc.network().totalDistance()).isEqualTo(4000);
		assertThat(acc.network().totalTravelTime()).isEqualTo(500);
	}

	@Test
	void resetForgetsEverything() {
		MobilityConsumptionAccumulator acc = new MobilityConsumptionAccumulator(P);
		acc.add(seg("a", "car", 0, 100, 1000, 100));
		acc.recordAborted();
		acc.reset();
		assertThat(acc.links()).isEmpty();
		assertThat(acc.modes()).isEmpty();
		assertThat(acc.network().totalConsumption()).isEqualTo(0);
		assertThat(acc.network().totalSegments()).isEqualTo(0);
		assertThat(acc.abortedSegments()).isEqualTo(0);
	}

	@Test
	void transitVehiclesAreNotSampledButTheirPassengersAre() {
		MobilityConsumptionParameters tenPercent = new MobilityConsumptionParameters(4.87, 6.25, 1.23, 900, 0, 86400, 0.1);
		MobilityConsumptionAccumulator acc = new MobilityConsumptionAccumulator(tenPercent);
		TraversalSegment bus = new TraversalSegment(Id.createLinkId("a"), Id.create("bus", Vehicle.class), null, "pt:bus",
			SegmentKind.FULL, 0, 100, 1000, 100, 18.25, ConsumptionSource.TRANSIT, 3.0);
		acc.add(bus);
		double raw = acc.getCalculator().consumption(bus);
		// Written values are raw * 10; the bus itself must come out at 1x, its 3 sampled passengers at 30.
		assertThat(acc.network().totalConsumption() * tenPercent.upscaleFactor()).isCloseTo(raw, within(1e-9));
		assertThat(acc.network().totalDistance() * tenPercent.upscaleFactor()).isCloseTo(1000, within(1e-9));
		assertThat(acc.network().totalPassengerDistance() * tenPercent.upscaleFactor()).isCloseTo(30000, within(1e-9));
		TraversalSegment car = seg("a", "car", 0, 100, 1000, 100);
		acc.add(car);
		assertThat(acc.modes().get("car").totalConsumption()).isCloseTo(acc.getCalculator().consumption(car), within(1e-9));
	}
}

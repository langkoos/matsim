package org.matsim.contrib.mobilityconsumption.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.vehicles.Vehicle;

class MobilityConsumptionCalculatorTest {

	private static final MobilityConsumptionParameters P = MobilityConsumptionParameters.DEFAULTS;
	private final MobilityConsumptionCalculator calc = new MobilityConsumptionCalculator(P);

	static TraversalSegment segment(double enter, double leave, double distance, double freeFlow) {
		return new TraversalSegment(Id.createLinkId("l"), Id.create("v", Vehicle.class), Id.create("p", Person.class),
			"car", SegmentKind.FULL, enter, leave, distance, freeFlow, P.lambda());
	}

	@Test
	void consumptionIsLambdaTimePlusTauDistance() {
		// 1000 m in 100 s at free flow (free-flow time 100 s): lambda*100 + tau*1000, no excess.
		TraversalSegment s = segment(0, 100, 1000, 100);
		assertThat(calc.consumption(s)).isCloseTo(11.12 * 100 + 1.23 * 1000, within(1e-9));
		assertThat(calc.excess(s)).isEqualTo(0);
	}

	@Test
	void excessIsClippedAtZeroAndOnlyCountsTime() {
		TraversalSegment slow = segment(0, 250, 1000, 100);
		assertThat(calc.excess(slow)).isCloseTo(11.12 * 150, within(1e-9));
		TraversalSegment fast = segment(0, 50, 1000, 100);
		assertThat(calc.excess(fast)).isEqualTo(0);
	}

	@Test
	void splitSharesSumToWhole() {
		// Starts 100 s before a bin boundary, ends 1900 s later: touches three bins.
		TraversalSegment s = segment(800, 2700, 1900, 1000);
		List<BinShare> shares = calc.split(s);
		assertThat(shares).extracting(BinShare::bin).containsExactly(0, 1, 2);
		assertThat(shares).extracting(BinShare::travelTime).containsExactly(100.0, 900.0, 900.0);
		assertThat(shares.stream().mapToDouble(BinShare::consumption).sum()).isCloseTo(calc.consumption(s), within(1e-9));
		assertThat(shares.stream().mapToDouble(BinShare::excess).sum()).isCloseTo(calc.excess(s), within(1e-9));
		assertThat(shares.stream().mapToDouble(BinShare::distance).sum()).isCloseTo(1900, within(1e-9));
		// Uniform speed: the first bin gets 100/1900 of everything.
		assertThat(shares.get(0).distance()).isCloseTo(100, within(1e-9));
		assertThat(shares.get(0).excess()).isCloseTo(calc.excess(s) * 100 / 1900, within(1e-9));
	}

	@Test
	void zeroDurationSegmentGoesToBinOfEnterTime() {
		TraversalSegment s = segment(950, 950, 0, 0);
		List<BinShare> shares = calc.split(s);
		assertThat(shares).hasSize(1);
		assertThat(shares.get(0).bin()).isEqualTo(1);
		assertThat(shares.get(0).consumption()).isEqualTo(0);
	}

	@Test
	void departureWaitingIsAllExcess() {
		TraversalSegment s = new TraversalSegment(Id.createLinkId("l"), Id.create("v", Vehicle.class), null, "car",
			SegmentKind.DEPARTURE, 0, 30, 0, 0, P.lambda());
		assertThat(calc.consumption(s)).isCloseTo(11.12 * 30, within(1e-9));
		assertThat(calc.excess(s)).isCloseTo(calc.consumption(s), within(1e-9));
	}

	@Test
	void segmentsOutsideWindowLandInOutsideBin() {
		MobilityConsumptionParameters p = new MobilityConsumptionParameters(4.87, 6.25, 1.23, 900, 3600, 7200, 1.0);
		MobilityConsumptionCalculator c = new MobilityConsumptionCalculator(p);
		// Straddles the window start.
		List<BinShare> before = c.split(segment(3000, 4000, 1000, 1000));
		assertThat(before).extracting(BinShare::bin).containsExactly(p.outsideBin(), 0);
		assertThat(before.get(0).travelTime()).isEqualTo(600);
		// Entirely after the window end.
		List<BinShare> after = c.split(segment(8000, 9000, 1000, 1000));
		assertThat(after).extracting(BinShare::bin).containsExactly(p.outsideBin());
		assertThat(after.get(0).travelTime()).isEqualTo(1000);
		// Straddles the window end.
		List<BinShare> across = c.split(segment(7000, 7400, 400, 400));
		assertThat(across).extracting(BinShare::bin).containsExactly(3, p.outsideBin());
	}

	@Test
	void sampleSizeDoesNotChangeRawConsumption() {
		MobilityConsumptionParameters tenPercent = new MobilityConsumptionParameters(4.87, 6.25, 1.23, 900, 0, 86400, 0.1);
		MobilityConsumptionCalculator c = new MobilityConsumptionCalculator(tenPercent);
		TraversalSegment s = segment(0, 100, 1000, 100);
		assertThat(c.consumption(s)).isEqualTo(calc.consumption(s));
		assertThat(tenPercent.upscaleFactor()).isCloseTo(10, within(1e-12));
	}
}

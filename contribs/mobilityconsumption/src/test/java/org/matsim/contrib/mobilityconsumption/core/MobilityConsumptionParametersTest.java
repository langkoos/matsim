package org.matsim.contrib.mobilityconsumption.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

class MobilityConsumptionParametersTest {

	@Test
	void paperDefaults() {
		MobilityConsumptionParameters p = MobilityConsumptionParameters.DEFAULTS;
		assertThat(p.lambda()).isCloseTo(11.12, within(1e-9));
		assertThat(p.reactionTime()).isEqualTo(1.23);
		assertThat(p.binCount()).isEqualTo(96);
		assertThat(p.outsideBin()).isEqualTo(96);
		assertThat(p.upscaleFactor()).isEqualTo(1.0);
		assertThat(p.analysisDuration()).isEqualTo(86400);
	}

	@Test
	void binIndexingCoversWindowAndOutside() {
		MobilityConsumptionParameters p = MobilityConsumptionParameters.DEFAULTS;
		assertThat(p.binIndex(0)).isEqualTo(0);
		assertThat(p.binIndex(899.9)).isEqualTo(0);
		assertThat(p.binIndex(900)).isEqualTo(1);
		assertThat(p.binIndex(86399)).isEqualTo(95);
		assertThat(p.binIndex(86400)).isEqualTo(96);
		assertThat(p.binIndex(-1)).isEqualTo(96);
		assertThat(p.binStart(95)).isEqualTo(85500);
		assertThat(p.binEnd(95)).isEqualTo(86400);
	}

	@Test
	void partialLastBinIsCapped() {
		MobilityConsumptionParameters p = new MobilityConsumptionParameters(4.87, 6.25, 1.23, 1000, 0, 2500, 1.0);
		assertThat(p.binCount()).isEqualTo(3);
		assertThat(p.binEnd(2)).isEqualTo(2500);
		assertThat(p.binIndex(2499)).isEqualTo(2);
	}

	@Test
	void unitConversion() {
		assertThat(MobilityConsumptionParameters.toKilometreHours(3.6e6)).isEqualTo(1.0);
	}

	@Test
	void rejectsInvalidValues() {
		assertThatThrownBy(() -> new MobilityConsumptionParameters(-1, 6.25, 1.23, 900, 0, 86400, 1.0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MobilityConsumptionParameters(4.87, 6.25, 1.23, 0, 0, 86400, 1.0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MobilityConsumptionParameters(4.87, 6.25, 1.23, 900, 100, 100, 1.0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MobilityConsumptionParameters(4.87, 6.25, 1.23, 900, 0, 86400, 0))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new MobilityConsumptionParameters(4.87, 6.25, 1.23, 900, 0, 86400, 1.5))
			.isInstanceOf(IllegalArgumentException.class);
	}
}

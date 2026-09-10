package org.matsim.contrib.mobilityconsumption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.contrib.mobilityconsumption.core.CollectorSettings;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionParameters;
import org.matsim.contrib.mobilityconsumption.core.VehicleLengthSource;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.testcases.MatsimTestUtils;

class MobilityConsumptionConfigGroupTest {

	@RegisterExtension
	private final MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	void sampleSizeFallsBackToSimWrapperThenFlowCapacity() {
		Config config = ConfigUtils.createConfig();
		MobilityConsumptionConfigGroup group = ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class);
		config.qsim().setFlowCapFactor(0.1);
		assertThat(group.resolveSampleSize(config)).isEqualTo(0.1);

		ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class).setSampleSize(0.25);
		assertThat(group.resolveSampleSize(config)).isEqualTo(0.25);

		group.setSampleSize(0.5);
		assertThat(group.resolveSampleSize(config)).isEqualTo(0.5);
		assertThat(group.toParameters(config).upscaleFactor()).isEqualTo(2.0);
	}

	@Test
	void parametersAndSettingsComeFromTheGroup() {
		Config config = ConfigUtils.createConfig();
		MobilityConsumptionConfigGroup group = ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class);
		MobilityConsumptionParameters p = group.toParameters(config);
		assertThat(p).isEqualTo(MobilityConsumptionParameters.DEFAULTS);
		CollectorSettings s = group.toCollectorSettings(config);
		assertThat(s).isEqualTo(CollectorSettings.CAR_DEFAULTS);

		group.setNetworkModes(Set.of("car", "truck"));
		group.setVehicleLengthSource(VehicleLengthSource.vehicleType);
		group.setIncludeDepartureSegments(false);
		config.qsim().setTimeStepSize(2.0);
		s = group.toCollectorSettings(config);
		assertThat(s.networkModes()).containsExactlyInAnyOrder("car", "truck");
		assertThat(s.vehicleLengthSource()).isEqualTo(VehicleLengthSource.vehicleType);
		assertThat(s.includeDepartureSegments()).isFalse();
		assertThat(s.timeStepSize()).isEqualTo(2.0);
	}

	@Test
	void consistencyCheckRejectsBadWindow() {
		Config config = ConfigUtils.createConfig();
		MobilityConsumptionConfigGroup group = ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class);
		group.setAnalysisEnd(0);
		assertThatThrownBy(config::checkConsistency).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void roundTripsThroughXml() {
		Config config = ConfigUtils.createConfig();
		MobilityConsumptionConfigGroup group = ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class);
		group.setTimeBinSize(600);
		group.setSampleSize(0.01);
		group.setNetworkModes(Set.of("car", "truck"));
		group.setWriteSegments(true);
		group.setVehicleLengthSource(VehicleLengthSource.vehicleType);
		String file = utils.getOutputDirectory() + "config.xml";
		new ConfigWriter(config).write(file);

		Config read = ConfigUtils.loadConfig(file, new MobilityConsumptionConfigGroup());
		MobilityConsumptionConfigGroup back = ConfigUtils.addOrGetModule(read, MobilityConsumptionConfigGroup.class);
		assertThat(back.getTimeBinSize()).isEqualTo(600);
		assertThat(back.getSampleSize()).isEqualTo(0.01);
		assertThat(back.getNetworkModes()).containsExactlyInAnyOrder("car", "truck");
		assertThat(back.isWriteSegments()).isTrue();
		assertThat(back.getVehicleLengthSource()).isEqualTo(VehicleLengthSource.vehicleType);
		assertThat(back.getWriteInterval()).isEqualTo(10);
	}
}

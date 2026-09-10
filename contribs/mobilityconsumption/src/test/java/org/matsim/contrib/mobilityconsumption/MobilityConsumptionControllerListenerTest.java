package org.matsim.contrib.mobilityconsumption;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.testcases.MatsimTestUtils;

class MobilityConsumptionControllerListenerTest {

	@RegisterExtension
	private final MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	void writesOnlyTheLastIterationWhenIntervalIsZero() {
		Config config = ConfigUtils.loadConfig(IOUtils.extendUrl(ExamplesUtils.getTestScenarioURL("equil"), "config.xml"));
		config.controller().setOutputDirectory(utils.getOutputDirectory());
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controller().setLastIteration(1);
		config.controller().setCreateGraphs(false);
		config.controller().setWriteEventsInterval(0);
		config.controller().setWritePlansInterval(0);
		config.controller().setDumpDataAtEnd(false);
		// Sample size comes from simwrapper when the module is present and ours is unset.
		ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class).setSampleSize(0.5);
		config.qsim().setFlowCapFactor(0.5);
		config.qsim().setStorageCapFactor(0.5);
		MobilityConsumptionConfigGroup group = ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class);
		group.setWriteInterval(0);
		group.setTimeBinSize(3600);

		Controler controler = new Controler(config);
		controler.addOverridingModule(new MobilityConsumptionModule());
		controler.run();

		Path out = Path.of(utils.getOutputDirectory());
		assertThat(out.resolve("ITERS/it.0/0.mobilityConsumption_links.csv.zst")).doesNotExist();
		assertThat(out.resolve("ITERS/it.1/1.mobilityConsumption_links.csv.zst")).exists();
		assertThat(out.resolve("ITERS/it.1/1.mobilityConsumption_segments.csv.zst")).doesNotExist();
		assertThat(out.resolve("output_mobilityConsumption_links.csv.zst")).exists();
		assertThat(out.resolve("output_mobilityConsumption_segments.csv.zst")).doesNotExist();
		assertThat(out.resolve("mobilityConsumption_stats.csv")).content().contains("\n1;0.5;");

		MobilityConsumptionControllerListener listener =
			controler.getInjector().getInstance(MobilityConsumptionControllerListener.class);
		assertThat(listener.isWrittenIteration(3, false)).isFalse();
		assertThat(listener.isWrittenIteration(3, true)).isTrue();
		group.setWriteInterval(2);
		assertThat(listener.isWrittenIteration(3, false)).isFalse();
		assertThat(listener.isWrittenIteration(4, false)).isTrue();
		assertThat(listener.getProduction().getParameters().sampleSize()).isEqualTo(0.5);
		assertThat(listener.getAccumulator().network().totalSegments()).isGreaterThan(0);
	}

	@Test
	void shutdownBeforeAnyWrittenIterationDoesNothing() {
		Config config = ConfigUtils.createConfig();
		config.controller().setOutputDirectory(utils.getOutputDirectory());
		ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class);
		org.matsim.api.core.v01.Scenario scenario = org.matsim.core.scenario.ScenarioUtils.createScenario(config);
		OutputDirectoryHierarchy io = new OutputDirectoryHierarchy(utils.getOutputDirectory(),
			OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists,
			config.controller().getCompressionType());
		MobilityConsumptionControllerListener listener = new MobilityConsumptionControllerListener(config,
			new org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionAccumulator(
				org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionParameters.DEFAULTS),
			scenario, io);
		listener.notifyShutdown(new org.matsim.core.controler.events.ShutdownEvent(null, false, 0, null));
		assertThat(Path.of(utils.getOutputDirectory(), "output_mobilityConsumption_stats.csv")).doesNotExist();
	}
}

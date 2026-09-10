package org.matsim.contrib.mobilityconsumption.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ServiceLoader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.common.conventions.vsp.SnzActivities;
import org.matsim.contrib.mobilityconsumption.MobilityConsumptionConfigGroup;
import org.matsim.contrib.mobilityconsumption.MobilityConsumptionModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControllerConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.simwrapper.DashboardProvider;
import org.matsim.simwrapper.SimWrapper;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.SimWrapperModule;
import org.matsim.testcases.MatsimTestUtils;

class MobilityConsumptionDashboardTest {

	@RegisterExtension
	private final MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	void generatesTheExpectedYaml() throws IOException {
		Config config = ConfigUtils.createConfig();
		ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class).setSampleSize(0.1);
		SimWrapper sw = SimWrapper.create(config);
		sw.addDashboard(new MobilityConsumptionDashboard("EPSG:25832", true));
		sw.generate(Path.of(utils.getOutputDirectory()));

		File actual = new File(utils.getOutputDirectory(), "dashboard-1.yaml");
		assertThat(actual).exists();
		// Compare as parsed documents: map key order inside colour ramps is not stable across runs.
		ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
		assertThat(yaml.readValue(actual, Object.class))
			.isEqualTo(yaml.readValue(new File(utils.getInputDirectory(), "dashboard.yaml"), Object.class));

		SimWrapper postHoc = SimWrapper.create(config);
		postHoc.addDashboard(new MobilityConsumptionDashboard("EPSG:25832", false));
		postHoc.generate(Path.of(utils.getOutputDirectory(), "posthoc"));
		assertThat(Files.readString(Path.of(utils.getOutputDirectory(), "posthoc", "dashboard-1.yaml")))
			.doesNotContain("Consumption over iterations");
	}

	@Test
	void providerIsFoundAndKeyedOnTheConfigModule() {
		Config config = ConfigUtils.createConfig();
		SimWrapper sw = SimWrapper.create(config);
		MobilityConsumptionDashboardProvider provider = new MobilityConsumptionDashboardProvider();
		assertThat(provider.getDashboards(config, sw)).isEmpty();
		ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class);
		assertThat(provider.getDashboards(config, sw)).hasSize(1);
		assertThat(provider.isDefault()).isTrue();
		assertThat(ServiceLoader.load(DashboardProvider.class).stream().map(p -> p.type().getSimpleName()))
			.contains("MobilityConsumptionDashboardProvider");
	}

	/** A kelheim run with SimWrapper produces the dashboard and every file it references. */
	@Test
	void runProducesDashboardAndAnalysisFiles() throws IOException {
		URL context = ExamplesUtils.getTestScenarioURL("kelheim");
		Config config = ConfigUtils.loadConfig(IOUtils.extendUrl(context, "config.xml"));
		config.controller().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
		config.controller().setCompressionType(ControllerConfigGroup.CompressionType.gzip);
		config.controller().setOutputDirectory(utils.getOutputDirectory());
		config.controller().setLastIteration(0);
		config.controller().setWriteEventsInterval(1);
		config.controller().setCreateGraphs(false);
		config.global().setRelativeToleranceForSampleSizeFactors(1.);
		SimWrapperConfigGroup group = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
		group.setSampleSize(0.001);
		// Only this dashboard, so the run stays small; it is registered as a default via SPI.
		group.setInclude(java.util.Set.of("MobilityConsumptionDashboard"));
		ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class).setWriteInterval(1);
		// The kelheim example needs the SNZ activity scoring and freight allowed on car links (as in simwrapper's tests).
		SnzActivities.addScoringParams(config);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		for (Link link : scenario.getNetwork().getLinks().values()) {
			if (link.getAllowedModes().contains("car")) {
				java.util.Set<String> modes = new java.util.HashSet<>(link.getAllowedModes());
				modes.add("freight");
				link.setAllowedModes(modes);
			}
		}

		Controler controler = new Controler(scenario);
		controler.addOverridingModule(new SimWrapperModule());
		controler.addOverridingModule(new MobilityConsumptionModule());
		controler.run();

		Path out = Path.of(utils.getOutputDirectory());
		Path analysis = out.resolve("analysis/mobilityconsumption");
		assertThat(analysis).isDirectoryContaining("glob:**mc_links_daily.csv")
			.isDirectoryContaining("glob:**mc_links_utilization_wide.csv")
			.isDirectoryContaining("glob:**mc_links_excess_ratio_wide.csv")
			.isDirectoryContaining("glob:**mc_network_bins.csv")
			.isDirectoryContaining("glob:**mc_tiles.csv")
			.isDirectoryContaining("glob:**mc_grid_bins.avro");
		assertThat(out.resolve("analysis/network/network.avro")).exists();
		String yaml = Files.readString(out.resolve("dashboard-1.yaml"));
		assertThat(yaml).contains("Mobility Consumption").contains("useSlider: true")
			.contains("analysis/mobilityconsumption/mc_links_utilization_wide.csv")
			.contains("mobilityConsumption_stats.csv");
		// Sample size came from simwrapper: consumption is upscaled 1000-fold relative to production.
		String stats = Files.readString(out.resolve("kelheim-mini.mobilityConsumption_stats.csv"));
		assertThat(stats).contains("\n0;0.001;");

		// The post-hoc command appends a second copy of the dashboard to the finished run and reruns the analysis.
		Files.delete(analysis.resolve("mc_tiles.csv"));
		CreateMobilityConsumptionDashboard.main(new String[]{"--sample-size", "0.001", out.toString()});
		assertThat(out.resolve("dashboard-2.yaml")).exists();
		assertThat(Files.readString(out.resolve("dashboard-2.yaml"))).contains("mobilityConsumption_stats.csv");
		assertThat(analysis.resolve("mc_tiles.csv")).exists();
		assertThat(CreateMobilityConsumptionDashboard.hasStatsFile(out)).isTrue();
		assertThat(CreateMobilityConsumptionDashboard.hasStatsFile(analysis)).isFalse();
		// Without --sample-size the run's own simwrapper.sampleSize applies.
		CreateMobilityConsumptionDashboard.main(new String[]{out.toString()});
		assertThat(out.resolve("dashboard-3.yaml")).exists();
	}
}

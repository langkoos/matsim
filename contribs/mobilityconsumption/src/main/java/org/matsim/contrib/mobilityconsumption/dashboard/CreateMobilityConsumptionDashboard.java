package org.matsim.contrib.mobilityconsumption.dashboard;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.application.ApplicationUtils;
import org.matsim.application.MATSimAppCommand;
import org.matsim.contrib.mobilityconsumption.MobilityConsumptionConfigGroup;
import org.matsim.contrib.mobilityconsumption.MobilityConsumptionControllerListener;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionParameters;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.simwrapper.SimWrapper;
import org.matsim.simwrapper.SimWrapperConfigGroup;

import picocli.CommandLine;

/**
 * Appends the mobility consumption dashboard to finished runs and runs the analysis that feeds it.
 * Usage: {@code CreateMobilityConsumptionDashboard --sample-size 0.1 /path/to/run ...}.
 */
@CommandLine.Command(name = "mobility-consumption-dashboard",
	description = "Run the mobility consumption analysis and add its SimWrapper dashboard to existing run output.")
public final class CreateMobilityConsumptionDashboard implements MATSimAppCommand {

	private static final Logger log = LogManager.getLogger(CreateMobilityConsumptionDashboard.class);

	@CommandLine.Parameters(arity = "1..*", description = "Run output directories.")
	private List<Path> inputPaths;

	@CommandLine.Option(names = "--sample-size", description = "Sample size of the runs; overrides the config.")
	private Double sampleSize;

	public static void main(String[] args) {
		new CreateMobilityConsumptionDashboard().execute(args);
	}

	/** True when the run wrote the per-iteration stats file, with or without a run id prefix. */
	static boolean hasStatsFile(Path runDirectory) throws IOException {
		try (var files = Files.list(runDirectory)) {
			return files.anyMatch(p -> p.getFileName().toString().endsWith(MobilityConsumptionControllerListener.STATS_FILE));
		}
	}

	@Override
	public Integer call() throws Exception {
		for (Path runDirectory : inputPaths) {
			log.info("Creating mobility consumption dashboard in {}", runDirectory);
			Path configPath = ApplicationUtils.matchInput("config.xml", runDirectory);
			Config config = ConfigUtils.loadConfig(configPath.toString());
			SimWrapperConfigGroup simwrapper = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class);
			simwrapper.setDefaultDashboards(SimWrapperConfigGroup.DefaultDashboardsMode.disabled);
			if (sampleSize != null) {
				simwrapper.setSampleSize(sampleSize);
			}
			SimWrapper sw = SimWrapper.create(config);
			boolean withSeries = hasStatsFile(runDirectory);
			double binSize = ConfigUtils.hasModule(config, MobilityConsumptionConfigGroup.class)
				? ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class).getTimeBinSize()
				: MobilityConsumptionParameters.DEFAULTS.timeBinSize();
			sw.addDashboard(new MobilityConsumptionDashboard(config.global().getCoordinateSystem(), withSeries, binSize));
			try {
				sw.generate(runDirectory, true);
				sw.run(runDirectory);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
		return 0;
	}
}

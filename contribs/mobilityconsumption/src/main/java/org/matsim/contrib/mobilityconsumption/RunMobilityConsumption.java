package org.matsim.contrib.mobilityconsumption;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;

/**
 * Runs a MATSim scenario with mobility consumption outputs. Usage: {@code RunMobilityConsumption config.xml}.
 * The config may contain a {@code mobilityConsumption} module; defaults apply otherwise.
 */
public final class RunMobilityConsumption {

	private RunMobilityConsumption() {
	}

	public static void main(String[] args) {
		Config config = ConfigUtils.loadConfig(args, new MobilityConsumptionConfigGroup());
		prepare(config).run();
	}

	/** Creates a controler with the module installed; separated from main so it can be tested. */
	public static Controler prepare(Config config) {
		ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class);
		Scenario scenario = ScenarioUtils.loadScenario(config);
		Controler controler = new Controler(scenario);
		controler.addOverridingModule(new MobilityConsumptionModule());
		return controler;
	}
}

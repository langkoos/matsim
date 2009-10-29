/* *********************************************************************** *
 * project: org.matsim.*
 * EquilnetDemo.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.mmoyo.demo;

import java.util.EnumSet;

import org.matsim.api.core.v01.ScenarioImpl;
import org.matsim.core.config.Config;
import org.matsim.core.config.Module;
import org.matsim.core.config.groups.ControlerConfigGroup.EventsFileFormat;
import org.matsim.core.scenario.ScenarioLoader;
import playground.mmoyo.TransitSimulation.MMoyoTransitControler;

/**copy of marcel.pt.demo.equilNet.EquilnetDemo.java to test the ptRouter in the simulation*/
public class ScenarioDemo {
	static String networkFile;
	static String plansFile;
	static String outputDirectory;
	static String transitScheduleFile;
	static String vehiclesFile;
	static boolean launchOTFDemo=false;
	
	private final ScenarioImpl scenario = new ScenarioImpl();

	private void prepareConfig() {
		Config config = this.scenario.getConfig();
		config.network().setInputFile(networkFile);
		config.plans().setInputFile(plansFile);  
		config.controler().setOutputDirectory(outputDirectory);
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(0);
		config.controler().setEventsFileFormats(EnumSet.of(EventsFileFormat.xml));
		config.controler().addParam("routingAlgorithmType", "AStarLandmarks");
		config.charyparNagelScoring().addParam("activityType_0", "h");
		config.charyparNagelScoring().addParam("activityPriority_0", "1");
		config.charyparNagelScoring().addParam("activityTypicalDuration_0", "12:00:00");
		config.charyparNagelScoring().addParam("activityMinimalDuration_0", "18:00:00");
		config.charyparNagelScoring().addParam("activityType_1", "w");
		config.charyparNagelScoring().addParam("activityPriority_1", "1");
		config.charyparNagelScoring().addParam("activityTypicalDuration_1", "08:00:00");
		config.charyparNagelScoring().addParam("activityMinimalDuration_1", "06:00:00");
		config.charyparNagelScoring().addParam("activityOpeningTime_1", "07:00:00");
		config.simulation().setEndTime(30.0*3600);
		config.strategy().addParam("maxAgentPlanMemorySize", "5");
		config.strategy().addParam("ModuleProbability_1", "0.1");
		config.strategy().addParam("Module_1", "TimeAllocationMutator");
		config.strategy().addParam("ModuleProbability_2", "0.1");
		config.strategy().addParam("Module_2", "ReRoute");
		config.strategy().addParam("ModuleProbability_3", "0.1");
		config.strategy().addParam("Module_3", "ChangeLegMode");
		config.strategy().addParam("ModuleProbability_4", "0.1");
		config.strategy().addParam("Module_4", "SelectExpBeta");
		Module changeLegModeModule = config.createModule("changeLegMode");
		changeLegModeModule.addParam("modes", "car,pt");
		Module transitModule = config.createModule("transit");
		transitModule.addParam("transitScheduleFile", transitScheduleFile);
		transitModule.addParam("vehiclesFile", vehiclesFile);
		transitModule.addParam("transitModes", "pt");
	}

	private void runControler() {
		new ScenarioLoader(this.scenario).loadScenario();
		MMoyoTransitControler c = new MMoyoTransitControler(this.scenario, launchOTFDemo);
		c.setOverwriteFiles(true);
		c.run();
	}

	public void run() {
		prepareConfig();
		runControler();
	}

	public static void main(final String[] args, boolean launchOTF) {
		networkFile= args[0];
		plansFile= args[1];
		outputDirectory= args[2];
		transitScheduleFile= args[3];
		vehiclesFile= args[4];
		launchOTFDemo = launchOTF;
		new ScenarioDemo().run();
	}
}

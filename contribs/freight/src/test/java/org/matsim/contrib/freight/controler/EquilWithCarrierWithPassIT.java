/* *********************************************************************** *
 * project: org.matsim.*
 * EquilWithCarrierTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2011 by the members listed in the COPYING,        *
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

package org.matsim.contrib.freight.controler;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.freight.carrier.Carrier;
import org.matsim.contrib.freight.carrier.CarrierPlanXmlReaderV2;
import org.matsim.contrib.freight.carrier.Carriers;
import org.matsim.contrib.freight.mobsim.DistanceScoringFunctionFactoryForTests;
import org.matsim.contrib.freight.mobsim.StrategyManagerFactoryForTests;
import org.matsim.contrib.freight.replanning.CarrierPlanStrategyManagerFactory;
import org.matsim.contrib.freight.scoring.CarrierScoringFunctionFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ActivityParams;
import org.matsim.core.config.groups.StrategyConfigGroup.StrategySettings;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.testcases.MatsimTestUtils;

public class EquilWithCarrierWithPassIT {
	
	Controler controler;

	private String planFile;

	@Rule
	public MatsimTestUtils testUtils = new MatsimTestUtils();

	@Before
	public void setUp() throws Exception{
		String NETWORK_FILENAME = testUtils.getClassInputDirectory() + "network.xml";
		String PLANS_FILENAME = testUtils.getClassInputDirectory() + "plans100.xml";
		Config config = new Config();
		config.addCoreModules();
		
		ActivityParams workParams = new ActivityParams("w");
		workParams.setTypicalDuration(60 * 60 * 8);
		config.planCalcScore().addActivityParams(workParams);
		ActivityParams homeParams = new ActivityParams("h");
		homeParams.setTypicalDuration(16 * 60 * 60);
		config.planCalcScore().addActivityParams(homeParams);
		config.global().setCoordinateSystem("EPSG:32632");
		config.controler().setFirstIteration(0);
		config.controler().setLastIteration(2);
		config.controler().setOutputDirectory(testUtils.getOutputDirectory());
		config.network().setInputFile(NETWORK_FILENAME);
		config.plans().setInputFile(PLANS_FILENAME);
		StrategySettings bestScore = new StrategySettings(Id.create("1", StrategySettings.class));
		bestScore.setStrategyName("BestScore");
		bestScore.setWeight(1.0);
		StrategySettings reRoute = new StrategySettings(Id.create("2", StrategySettings.class));
		reRoute.setStrategyName("ReRoute");
		reRoute.setWeight(0.0);
		reRoute.setDisableAfter(300);
		config.strategy().setMaxAgentPlanMemorySize(5);
		config.strategy().addStrategySettings(bestScore);
		config.strategy().addStrategySettings(reRoute);
		controler = new Controler(config);
		controler.getConfig().controler().setWriteEventsInterval(1);
        controler.getConfig().controler().setCreateGraphs(false);
		planFile = testUtils.getClassInputDirectory() + "carrierPlansEquils.xml";
	}

	@Test
	public void testScoringInMeters(){
		Carriers carriers = new Carriers();
		new CarrierPlanXmlReaderV2(carriers).readFile(planFile);
		controler.addOverridingModule(new CarrierModule(carriers));
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bind(CarrierPlanStrategyManagerFactory.class).to(StrategyManagerFactoryForTests.class).asEagerSingleton();
				bind(CarrierScoringFunctionFactory.class).to(DistanceScoringFunctionFactoryForTests.class).asEagerSingleton();
			}
		});
		controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);
		controler.run();

		Carrier carrier1 = controler.getInjector().getInstance(Carriers.class).getCarriers().get(Id.create("carrier1", Carrier.class));
		Assert.assertEquals(-170000.0, carrier1.getSelectedPlan().getScore().doubleValue(), 0.0);

		Carrier carrier2 = controler.getInjector().getInstance(Carriers.class).getCarriers().get(Id.create("carrier2", Carrier.class));
		Assert.assertEquals(-85000.0, carrier2.getSelectedPlan().getScore().doubleValue(), 0.0);
	}

}

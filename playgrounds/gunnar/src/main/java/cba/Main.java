package cba;

import java.util.Arrays;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.TravelTimeCalculatorConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripRouterModule;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioByInstanceModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ExperiencedPlansModule;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;

import com.google.inject.Provider;

import besttimeresponseintegration.ExperiencedScoreAnalyzer;
import matsimintegration.TimeDiscretizationInjection;

/**
 * 
 * @author Gunnar Flötteröd
 *
 */
public class Main {

	/*-
	 * ============================================================ 
	 *      DEMAND MODEL
	 * ============================================================
	 */

	private static void runDemandModel(final Scenario scenario, final int outerIt, final double replanProba) {

		if (outerIt == 1) {

			final int popSize = 100;
			DemandModel.initializePopulation(scenario, popSize);

		} else {

			final EventsManager events = EventsUtils.createEventsManager();
			final TravelTimeCalculator travelTimeCalculator = new TravelTimeCalculator(scenario.getNetwork(),
					(TravelTimeCalculatorConfigGroup) scenario.getConfig().getModule("travelTimeCalculator"));
			events.addHandler(travelTimeCalculator);
			final MatsimEventsReader reader = new MatsimEventsReader(events);
			final int lastIt = scenario.getConfig().controler().getLastIteration();
			reader.readFile("./testdata/cba/output/ITERS/it." + lastIt + "/" + lastIt + ".events.xml.gz");
			final TravelTime carTravelTime = travelTimeCalculator.getLinkTravelTimes();

			// >>>>>>>>>> COPY & PASTE FROM TripRouterImplTest.java >>>>>>>>>>

			/*
			 * TODO Does this now behave like the TripRouter in the last
			 * iteration of the last simulation run (from which the
			 * carTravelTime comes)? Where do the PT travel times come from?
			 */

			final com.google.inject.Injector injector = org.matsim.core.controler.Injector
					.createInjector(scenario.getConfig(), new AbstractModule() {
						@Override
						public void install() {
							install(AbstractModule.override(Arrays.asList(new TripRouterModule()),
									new AbstractModule() {
										@Override
										public void install() {
											install(new ScenarioByInstanceModule(scenario));
											addTravelTimeBinding("car").toInstance(carTravelTime);
										}
									}));
						}
					});
			final Provider<TripRouter> factory = injector.getProvider(TripRouter.class);

			// <<<<<<<<<< COPY & PASTE FROM TripRouterImplTest.java <<<<<<<<<<

			DemandModel.replanPopulation(scenario, factory, replanProba);
		}

		final PopulationWriter popwriter = new PopulationWriter(scenario.getPopulation(), scenario.getNetwork());
		popwriter.write("testdata/cba/triangle-population.xml");
	}

	/*-
	 * ============================================================ 
	 *      SUPPLY MODEL
	 * ============================================================
	 */

	private static void runSupplyModel(final Scenario scenario, final int outerIt) {
		final Controler controler = new Controler(scenario);
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addControlerListenerBinding().to(ExperiencedScoreAnalyzer.class);
				bind(ExperiencedScoreAnalyzer.class);
			}
		});
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				bind(TimeDiscretizationInjection.class);
			}
		});
		controler.addOverridingModule(new ExperiencedPlansModule());
		controler.run();
	}

	/*-
	 * ============================================================ 
	 *      MAIN LOOP
	 * ============================================================
	 */

	public static void main(String[] args) {

		final int outerIts = 5;

		System.out.println("STARTED");

		for (int outerIt = 1; outerIt <= outerIts; outerIt++) {

			{
				System.out.println("OUTER ITERATION " + outerIt + ", running DEMAND model");

				final Config config = ConfigUtils.loadConfig("./testdata/cba/config.xml");
				final Scenario scenario = ScenarioUtils.loadScenario(config);
				runDemandModel(scenario, outerIt, 0.1);
			}

			{
				System.out.println("OUTER ITERATION " + outerIt + ", running SUPPLY model");

				final Config config = ConfigUtils.loadConfig("./testdata/cba/config.xml");
				config.controler()
						.setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
				config.getModule("plans").addParam("inputPlansFile", "triangle-population.xml");
				final Scenario scenario = ScenarioUtils.loadScenario(config);

				runSupplyModel(scenario, outerIt);
			}

			System.out.println("DONE");
		}
	}
}
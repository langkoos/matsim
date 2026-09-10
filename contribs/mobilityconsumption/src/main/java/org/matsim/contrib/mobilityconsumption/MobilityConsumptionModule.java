package org.matsim.contrib.mobilityconsumption;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionAccumulator;
import org.matsim.contrib.mobilityconsumption.core.TraversalSegmentCollector;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;

import com.google.inject.Provides;
import com.google.inject.Singleton;

/**
 * Installs the mobility consumption event handler and output listener. The config group is added with its
 * defaults if absent, so {@code controler.addOverridingModule(new MobilityConsumptionModule())} is all a
 * user needs.
 */
public final class MobilityConsumptionModule extends AbstractModule {

	@Override
	public void install() {
		ConfigUtils.addOrGetModule(getConfig(), MobilityConsumptionConfigGroup.class);
		bind(MobilityConsumptionControllerListener.class).asEagerSingleton();
		addControllerListenerBinding().to(MobilityConsumptionControllerListener.class);
		addEventHandlerBinding().to(TraversalSegmentCollector.class);
	}

	@Provides
	@Singleton
	MobilityConsumptionAccumulator accumulator(Config config) {
		MobilityConsumptionConfigGroup group = ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class);
		return new MobilityConsumptionAccumulator(group.toParameters(config));
	}

	@Provides
	@Singleton
	TraversalSegmentCollector collector(Config config, Scenario scenario, MobilityConsumptionAccumulator accumulator,
			MobilityConsumptionControllerListener listener) {
		MobilityConsumptionConfigGroup group = ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class);
		return new TraversalSegmentCollector(scenario.getNetwork(), accumulator.getParameters(),
			group.toCollectorSettings(config), vehicleTypeLookup(scenario), listener, accumulator::recordAborted);
	}

	/** Looks a vehicle's type up in the scenario's vehicle containers; null when unknown. */
	static java.util.function.Function<Id<Vehicle>, VehicleType> vehicleTypeLookup(Scenario scenario) {
		return id -> {
			Vehicle vehicle = VehicleUtils.findVehicle(id, scenario);
			return vehicle == null ? null : vehicle.getType();
		};
	}
}

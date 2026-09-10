package org.matsim.contrib.mobilityconsumption.dashboard;

import java.util.List;

import org.matsim.contrib.mobilityconsumption.MobilityConsumptionConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.DashboardProvider;
import org.matsim.simwrapper.SimWrapper;

/**
 * Registers the dashboard through the Java service loader whenever the config contains the
 * {@code mobilityConsumption} module, so a run with the module and SimWrapper needs no further setup.
 */
public final class MobilityConsumptionDashboardProvider implements DashboardProvider {

	@Override
	public List<Dashboard> getDashboards(Config config, SimWrapper simWrapper) {
		if (!ConfigUtils.hasModule(config, MobilityConsumptionConfigGroup.class)) {
			return List.of();
		}
		MobilityConsumptionConfigGroup group = ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class);
		return List.of(new MobilityConsumptionDashboard(config.global().getCoordinateSystem(), true, group.getTimeBinSize()));
	}

	@Override
	public boolean isDefault() {
		return true;
	}
}

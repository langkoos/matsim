package org.matsim.contrib.mobilityconsumption;

import java.util.HashSet;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.mobilityconsumption.core.CollectorSettings;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionParameters;
import org.matsim.contrib.mobilityconsumption.core.VehicleLengthSource;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ReflectiveConfigGroup;
import org.matsim.simwrapper.SimWrapperConfigGroup;

/**
 * Configuration of the mobility consumption outputs. Add it to a config with
 * {@code ConfigUtils.addOrGetModule(config, MobilityConsumptionConfigGroup.class)}.
 */
public final class MobilityConsumptionConfigGroup extends ReflectiveConfigGroup {

	public static final String GROUP_NAME = "mobilityConsumption";

	private static final Logger log = LogManager.getLogger(MobilityConsumptionConfigGroup.class);

	@Parameter
	@Comment("Average vehicle length in metres. Together with vehicleSpacing it gives lambda, the road space one vehicle occupies. Default 4.87 (Bliemer et al. 2024).")
	private double vehicleLength = 4.87;

	@Parameter
	@Comment("Average bumper-to-bumper spacing in metres. Default 6.25, so lambda = 11.12 m.")
	private double vehicleSpacing = 6.25;

	@Parameter
	@Comment("Driver reaction time tau in seconds, weighting distance. Default 1.23 (NGSIM I-80).")
	private double reactionTime = 1.23;

	@Parameter
	@Comment("Width of a link-time bin in seconds. Default 900 (15 minutes).")
	private double timeBinSize = 900;

	@Parameter
	@Comment("Start of the analysis window in seconds. Mobility production (supply) is computed over the window.")
	private double analysisStart = 0;

	@Parameter
	@Comment("End of the analysis window in seconds. Default 86400. Consumption outside the window is reported in a separate 'outside' bin.")
	private double analysisEnd = 86400;

	@Parameter
	@Comment("Share of the population that is simulated, in (0, 1]. Consumption is scaled by its inverse; production is not. Leave empty to use simwrapper.sampleSize when that module is present, else qsim.flowCapacityFactor.")
	private Double sampleSize = null;

	@Parameter
	@Comment("Network modes (as reported by the vehicle-enters-traffic event) to include. Default: car.")
	private Set<String> networkModes = new HashSet<>(Set.of(TransportMode.car));

	@Parameter
	@Comment("Exclude vehicles driven by transit drivers even if they use an included network mode. Default true.")
	private boolean excludeTransitVehicles = true;

	@Parameter
	@Comment("Count the time a departing vehicle waits in the buffer at the end of its first link (zero distance, all excess). Default true. Set both this and includeArrivalSegments to false to count only link-to-link traversals as in Ai et al.")
	private boolean includeDepartureSegments = true;

	@Parameter
	@Comment("Count the traversal of the last link of a leg, which ends with vehicle-leaves-traffic rather than link-leave. Default true.")
	private boolean includeArrivalSegments = true;

	@Parameter
	@Comment("Source of lambda per vehicle: 'fixed' uses vehicleLength + vehicleSpacing for every vehicle; 'vehicleType' uses VehicleType.getLength() (MATSim's default 7.5 m already includes spacing).")
	private VehicleLengthSource vehicleLengthSource = VehicleLengthSource.fixed;

	@Parameter
	@Comment("Write the per-link and per-bin tables every n iterations (and in the last iteration). 0 writes only the last iteration. The per-iteration stats file is always written.")
	private int writeInterval = 10;

	@Parameter
	@Comment("Also write every traversal segment (vehicle, driver, link, times, consumption) in written iterations. Large; default false.")
	private boolean writeSegments = false;

	public MobilityConsumptionConfigGroup() {
		super(GROUP_NAME);
	}

	@Override
	protected void checkConsistency(Config config) {
		super.checkConsistency(config);
		toParameters(config);
	}

	/** Resolves the metric parameters, including the sample size fallback chain. */
	public MobilityConsumptionParameters toParameters(Config config) {
		return new MobilityConsumptionParameters(vehicleLength, vehicleSpacing, reactionTime, timeBinSize,
			analysisStart, analysisEnd, resolveSampleSize(config));
	}

	/** Resolves the collector settings; the time step comes from the qsim config. */
	public CollectorSettings toCollectorSettings(Config config) {
		return new CollectorSettings(networkModes, excludeTransitVehicles, includeDepartureSegments,
			includeArrivalSegments, vehicleLengthSource, config.qsim().getTimeStepSize());
	}

	/** Sample size: explicit value, else simwrapper.sampleSize when present, else qsim.flowCapacityFactor. */
	public double resolveSampleSize(Config config) {
		if (sampleSize != null) {
			return sampleSize;
		}
		if (ConfigUtils.hasModule(config, SimWrapperConfigGroup.class)) {
			Double fromSimWrapper = ConfigUtils.addOrGetModule(config, SimWrapperConfigGroup.class).getSampleSize();
			if (fromSimWrapper != null) {
				log.info("mobilityConsumption.sampleSize not set; using simwrapper.sampleSize = {}", fromSimWrapper);
				return fromSimWrapper;
			}
		}
		double flowCap = config.qsim().getFlowCapFactor();
		log.info("mobilityConsumption.sampleSize not set; using qsim.flowCapacityFactor = {}", flowCap);
		return flowCap;
	}

	public double getVehicleLength() {
		return vehicleLength;
	}

	public void setVehicleLength(double vehicleLength) {
		this.vehicleLength = vehicleLength;
	}

	public double getVehicleSpacing() {
		return vehicleSpacing;
	}

	public void setVehicleSpacing(double vehicleSpacing) {
		this.vehicleSpacing = vehicleSpacing;
	}

	public double getReactionTime() {
		return reactionTime;
	}

	public void setReactionTime(double reactionTime) {
		this.reactionTime = reactionTime;
	}

	public double getTimeBinSize() {
		return timeBinSize;
	}

	public void setTimeBinSize(double timeBinSize) {
		this.timeBinSize = timeBinSize;
	}

	public double getAnalysisStart() {
		return analysisStart;
	}

	public void setAnalysisStart(double analysisStart) {
		this.analysisStart = analysisStart;
	}

	public double getAnalysisEnd() {
		return analysisEnd;
	}

	public void setAnalysisEnd(double analysisEnd) {
		this.analysisEnd = analysisEnd;
	}

	public Double getSampleSize() {
		return sampleSize;
	}

	public void setSampleSize(Double sampleSize) {
		this.sampleSize = sampleSize;
	}

	public Set<String> getNetworkModes() {
		return networkModes;
	}

	public void setNetworkModes(Set<String> networkModes) {
		this.networkModes = new HashSet<>(networkModes);
	}

	public boolean isExcludeTransitVehicles() {
		return excludeTransitVehicles;
	}

	public void setExcludeTransitVehicles(boolean excludeTransitVehicles) {
		this.excludeTransitVehicles = excludeTransitVehicles;
	}

	public boolean isIncludeDepartureSegments() {
		return includeDepartureSegments;
	}

	public void setIncludeDepartureSegments(boolean includeDepartureSegments) {
		this.includeDepartureSegments = includeDepartureSegments;
	}

	public boolean isIncludeArrivalSegments() {
		return includeArrivalSegments;
	}

	public void setIncludeArrivalSegments(boolean includeArrivalSegments) {
		this.includeArrivalSegments = includeArrivalSegments;
	}

	public VehicleLengthSource getVehicleLengthSource() {
		return vehicleLengthSource;
	}

	public void setVehicleLengthSource(VehicleLengthSource vehicleLengthSource) {
		this.vehicleLengthSource = vehicleLengthSource;
	}

	public int getWriteInterval() {
		return writeInterval;
	}

	public void setWriteInterval(int writeInterval) {
		this.writeInterval = writeInterval;
	}

	public boolean isWriteSegments() {
		return writeSegments;
	}

	public void setWriteSegments(boolean writeSegments) {
		this.writeSegments = writeSegments;
	}
}

package org.matsim.contrib.mobilityconsumption.core;

/**
 * Immutable parameters of the mobility consumption metric.
 *
 * <p>Following Bliemer, Loder and Zheng (2024) and Ai et al. (TRB 2027), the road space-time consumed by a
 * traversal is {@code lambda * T + tau * D}, where {@code lambda} is the road space occupied by one vehicle
 * (vehicle length plus bumper-to-bumper spacing), {@code tau} the reaction time, {@code T} the time spent on
 * the link and {@code D} the distance travelled.
 *
 * @param vehicleLength  average vehicle length in metres (default 4.87)
 * @param vehicleSpacing average bumper-to-bumper spacing in metres (default 6.25)
 * @param reactionTime   driver reaction time in seconds (default 1.23)
 * @param timeBinSize    width of a link-time bin in seconds (default 900)
 * @param analysisStart  start of the analysis window in seconds (default 0)
 * @param analysisEnd    end of the analysis window in seconds (default 86400)
 * @param sampleSize     share of the population simulated, in (0, 1]; consumption is upscaled by its inverse
 */
public record MobilityConsumptionParameters(
	double vehicleLength,
	double vehicleSpacing,
	double reactionTime,
	double timeBinSize,
	double analysisStart,
	double analysisEnd,
	double sampleSize) {

	/** Paper defaults, 15-minute bins, a 24 h window and a 100% sample. */
	public static final MobilityConsumptionParameters DEFAULTS =
		new MobilityConsumptionParameters(4.87, 6.25, 1.23, 900, 0, 86400, 1.0);

	/** Conversion from metre-seconds (the internal unit) to kilometre-hours (the reported unit). */
	public static final double METRE_SECONDS_PER_KILOMETRE_HOUR = 1000.0 * 3600.0;

	public MobilityConsumptionParameters {
		if (vehicleLength < 0 || vehicleSpacing < 0 || reactionTime < 0) {
			throw new IllegalArgumentException("vehicleLength, vehicleSpacing and reactionTime must be non-negative");
		}
		if (timeBinSize <= 0) {
			throw new IllegalArgumentException("timeBinSize must be positive, got " + timeBinSize);
		}
		if (analysisEnd <= analysisStart) {
			throw new IllegalArgumentException("analysisEnd must be after analysisStart");
		}
		if (!(sampleSize > 0 && sampleSize <= 1.0)) {
			throw new IllegalArgumentException("sampleSize must be in (0, 1], got " + sampleSize);
		}
	}

	/** Road space occupied by one vehicle, lambda, in metres. */
	public double lambda() {
		return vehicleLength + vehicleSpacing;
	}

	/** Number of regular bins inside the analysis window. The last, partial bin is included. */
	public int binCount() {
		return (int) Math.ceil((analysisEnd - analysisStart) / timeBinSize);
	}

	/** Index of the extra bin that collects everything outside the analysis window. */
	public int outsideBin() {
		return binCount();
	}

	/** Bin index for a point in time; {@link #outsideBin()} when the time is outside the window. */
	public int binIndex(double time) {
		if (time < analysisStart || time >= analysisEnd) {
			return outsideBin();
		}
		return (int) Math.floor((time - analysisStart) / timeBinSize);
	}

	/** Start time of a regular bin in seconds. */
	public double binStart(int bin) {
		return analysisStart + bin * timeBinSize;
	}

	/** End time of a regular bin in seconds, capped at the analysis end. */
	public double binEnd(int bin) {
		return Math.min(analysisEnd, binStart(bin) + timeBinSize);
	}

	/** Factor that scales simulated consumption up to the full population. */
	public double upscaleFactor() {
		return 1.0 / sampleSize;
	}

	/** Length of the analysis window in seconds. */
	public double analysisDuration() {
		return analysisEnd - analysisStart;
	}

	/** Converts an internal metre-second value to kilometre-hours. */
	public static double toKilometreHours(double metreSeconds) {
		return metreSeconds / METRE_SECONDS_PER_KILOMETRE_HOUR;
	}
}

package org.matsim.simwrapper.viz;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Creates a Link Volume Map for simwrapper.
 */
public class Links extends VizMap<Links> {

	/**
	 * Sets the path of the network file.
	 */
	@JsonProperty(required = true)
	public String network;

	public Datasets datasets = new Datasets();

	/**
	 * Sets the projection of the map. E.g. EPSG:31468
	 */
	public String projection;

	/**
	 * Sets the center coordinates.
	 */
	public double[] center;

	/**
	 * Sets the initial zoom level of the map.
	 */
	public Double zoom;

	/**
	 * Set to true for this map to have independent center/zoom/motion
	 */
	public Boolean mapIsIndependent;

	/**
	 * Sets the display options for the map.
	 */
	public Display display = new Display();

	/**
	 * Show a time-of-day slider that steps through the dataset's columns (after the link id column) instead of a
	 * column picker. Name the columns by their bin start time, e.g. {@code 00:00, 00:15, ...}.
	 */
	public Boolean useSlider;

	/**
	 * Start with the difference {@code csvFile - csvBase} displayed; requires {@code datasets.csvBase}.
	 */
	public Boolean showDifferences;

	public Links() {
		super("links");
	}

	/**
	 * Sets the data to display
	 */
	public static final class Datasets {

		/**
		 * Sets the .csv file that includes the data.
		 */
		@JsonProperty(required = true)
		public String csvFile;

		public String csvBase;
	}

	/**
	 * Sets the display options.
	 */
	public static final class Display {

		public Color color = new Color();

		public Width width = new Width();

		/**
		 * Defines how the width should be calculated.
		 */
		public static final class Width {

			@JsonProperty(required = true)
			public String dataset;

			@JsonProperty(required = true)
			public String columnName;

			/** Values are divided by this factor to get pixels (clamped by the plugin to 0.25 to 50 px). */
			public Double scaleFactor;

		}

		/**
		 * Defines how the color is determined.
		 */
		public static final class Color {

			public String dataset;

			public String columnName;

			@JsonProperty(required = true)
			public String fixedColors;
		}
	}
}

package org.matsim.contrib.mobilityconsumption.dashboard;

import org.matsim.application.prepare.network.CreateAvroNetwork;
import org.matsim.contrib.mobilityconsumption.MobilityConsumptionControllerListener;
import org.matsim.contrib.mobilityconsumption.analysis.MobilityConsumptionAnalysis;
import org.matsim.contrib.mobilityconsumption.io.GridRasterWriter;
import org.matsim.simwrapper.Dashboard;
import org.matsim.simwrapper.DashboardUtils;
import org.matsim.simwrapper.Header;
import org.matsim.simwrapper.Layout;
import org.matsim.simwrapper.SimWrapperConfigGroup;
import org.matsim.simwrapper.viz.ColorScheme;
import org.matsim.simwrapper.viz.GridMap;
import org.matsim.simwrapper.viz.Links;
import org.matsim.simwrapper.viz.MapPlot;
import org.matsim.simwrapper.viz.Plotly;
import org.matsim.simwrapper.viz.TextBlock;
import org.matsim.simwrapper.viz.Tile;

import tech.tablesaw.plotly.components.Axis;
import tech.tablesaw.plotly.traces.ScatterTrace;

/**
 * Dashboard for mobility consumption: totals, time series, a daily link map, two time-animated link maps, an
 * animated grid and the per-link utilisation versus excess scatter.
 */
public final class MobilityConsumptionDashboard implements Dashboard {

	private final String coordinateSystem;
	private final boolean withIterationSeries;

	/**
	 * @param coordinateSystem    CRS of the network, for the grid map
	 * @param withIterationSeries include the per-iteration series the controller listener writes
	 */
	public MobilityConsumptionDashboard(String coordinateSystem, boolean withIterationSeries) {
		this.coordinateSystem = coordinateSystem;
		this.withIterationSeries = withIterationSeries;
	}

	@Override
	public double priority() {
		return -5;
	}

	@Override
	public void configure(Header header, Layout layout, SimWrapperConfigGroup configGroup) {
		header.title = "Mobility Consumption";
		header.description = "Road space-time consumed by car traffic (kilometre-hours), its congestion-related "
			+ "excess, and the share of the supplied road space-time that is used.";

		layout.row("tiles").el(Tile.class, (viz, data) -> {
			viz.dataset = data.compute(MobilityConsumptionAnalysis.class, MobilityConsumptionAnalysis.TILES);
			viz.height = 0.1;
		});

		Layout.Row series = layout.row("series");
		series.el(Plotly.class, (viz, data) -> {
			viz.title = "Utilisation by time of day";
			viz.description = "Consumption over production per bin, all modes and per mode.";
			Plotly.DataSet ds = viz.addDataset(data.compute(MobilityConsumptionAnalysis.class,
				MobilityConsumptionAnalysis.NETWORK_BINS)).filterNotIn("time", "outside");
			viz.layout = tech.tablesaw.plotly.components.Layout.builder()
				.xAxis(Axis.builder().title("Time").build())
				.yAxis(Axis.builder().title("MC / MP").build())
				.build();
			viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).mode(ScatterTrace.Mode.LINE).build(),
				ds.mapping().x("time").y("utilization").name("mode", ColorScheme.Spectral));
		});
		series.el(Plotly.class, (viz, data) -> {
			viz.title = "Excess ratio by time of day";
			viz.description = "Share of consumption caused by travel slower than free flow.";
			Plotly.DataSet ds = viz.addDataset(data.compute(MobilityConsumptionAnalysis.class,
				MobilityConsumptionAnalysis.NETWORK_BINS)).filterNotIn("time", "outside");
			viz.layout = tech.tablesaw.plotly.components.Layout.builder()
				.xAxis(Axis.builder().title("Time").build())
				.yAxis(Axis.builder().title("Excess ratio").build())
				.build();
			viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).mode(ScatterTrace.Mode.LINE).build(),
				ds.mapping().x("time").y("excess_ratio").name("mode", ColorScheme.Spectral));
		});
		if (withIterationSeries) {
			series.el(Plotly.class, (viz, data) -> {
				viz.title = "Consumption over iterations";
				viz.description = "Total and excess mobility consumption per iteration.";
				Plotly.DataSet ds = viz.addDataset(
					data.output("(*.)?" + MobilityConsumptionControllerListener.STATS_FILE));
				viz.layout = tech.tablesaw.plotly.components.Layout.builder()
					.xAxis(Axis.builder().title("Iteration").build())
					.yAxis(Axis.builder().title("km·h").build())
					.build();
				viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).mode(ScatterTrace.Mode.LINE)
					.name("MC").build(), ds.mapping().x("iteration").y("mc_kmh"));
				viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).mode(ScatterTrace.Mode.LINE)
					.name("MC excess").build(), ds.mapping().x("iteration").y("mc_excess_kmh"));
			});
		}

		layout.row("map").el(MapPlot.class, (viz, data) -> {
			viz.title = "Daily consumption per link";
			viz.description = DashboardUtils.adjustDescriptionBasedOnSampling(
				"Width: mobility consumption (km·h). Colour: excess ratio.", data, true);
			viz.height = 12d;
			viz.center = data.context().getCenter();
			viz.zoom = data.context().getMapZoomLevel();
			viz.setShape(data.compute(CreateAvroNetwork.class, "network.avro"), "id");
			viz.addDataset("mc", data.compute(MobilityConsumptionAnalysis.class, MobilityConsumptionAnalysis.LINKS_DAILY));
			viz.display.lineColor.dataset = "mc";
			viz.display.lineColor.columnName = "excess_ratio";
			viz.display.lineColor.join = "link_id";
			viz.display.lineColor.setColorRamp(ColorScheme.RdYlGn, 7, true);
			viz.display.lineWidth.dataset = "mc";
			viz.display.lineWidth.columnName = "mc_kmh";
			viz.display.lineWidth.join = "link_id";
			viz.display.lineWidth.scaleFactor = 50d;
		});

		Layout.Row animated = layout.row("animated");
		animated.el(Links.class, (viz, data) -> {
			viz.title = "Utilisation by time bin";
			viz.description = "Consumption over production per link and bin. Use the slider to step through the day.";
			viz.height = 12d;
			viz.network = data.compute(CreateAvroNetwork.class, "network.avro");
			viz.datasets.csvFile = data.compute(MobilityConsumptionAnalysis.class,
				MobilityConsumptionAnalysis.UTILIZATION_WIDE);
			viz.useSlider = true;
			viz.display.width.dataset = "csvFile";
			viz.display.width.columnName = "00:00";
			viz.display.width.scaleFactor = 1;
			viz.display.color.fixedColors = "#1f77b4";
		});
		animated.el(Links.class, (viz, data) -> {
			viz.title = "Excess ratio by time bin";
			viz.description = "Share of each link's consumption caused by delay, per bin.";
			viz.height = 12d;
			viz.network = data.compute(CreateAvroNetwork.class, "network.avro");
			viz.datasets.csvFile = data.compute(MobilityConsumptionAnalysis.class,
				MobilityConsumptionAnalysis.EXCESS_RATIO_WIDE);
			viz.useSlider = true;
			viz.display.width.dataset = "csvFile";
			viz.display.width.columnName = "00:00";
			viz.display.width.scaleFactor = 1;
			viz.display.color.fixedColors = "#d62728";
		});

		layout.row("grid").el(GridMap.class, (viz, data) -> {
			viz.title = "Consumption density by time bin";
			viz.description = "Mobility consumption per grid cell and bin (km·h), links spread over the cells they cross.";
			DashboardUtils.setGridMapStandards(viz, data, coordinateSystem);
			viz.cellSize = 250;
			viz.valueColumn = GridRasterWriter.CONSUMPTION_KEY;
			viz.timeSelector = GridMap.TimeSelector.slider;
			viz.setColorRamp(ColorScheme.Viridis, 8, false);
			viz.file = data.compute(MobilityConsumptionAnalysis.class, MobilityConsumptionAnalysis.GRID);
		});

		layout.row("scatter").el(Plotly.class, (viz, data) -> {
			viz.title = "Utilisation versus excess ratio per link";
			viz.description = "Each point is a link over the whole day (figure 5 of Ai et al.).";
			Plotly.DataSet ds = viz.addDataset(data.compute(MobilityConsumptionAnalysis.class,
				MobilityConsumptionAnalysis.LINKS_DAILY));
			viz.layout = tech.tablesaw.plotly.components.Layout.builder()
				.xAxis(Axis.builder().title("Utilisation MC / MP").build())
				.yAxis(Axis.builder().title("Excess ratio").build())
				.build();
			viz.addTrace(ScatterTrace.builder(Plotly.INPUT, Plotly.INPUT).mode(ScatterTrace.Mode.MARKERS).build(),
				ds.mapping().x("utilization").y("excess_ratio").text("link_id"));
		});

		layout.row("notes").el(TextBlock.class, (viz, data) -> {
			viz.backgroundColor = "transparent";
			viz.content = """
				### Definitions
				- Mobility consumption of a link traversal is `lambda * T + tau * D` with `T` the time on the link, `D` the distance, `lambda` the road space one vehicle occupies (4.87 m + 6.25 m spacing) and `tau` the reaction time (1.23 s); reported in kilometre-hours (Bliemer, Loder and Zheng 2024).
				- Excess consumption is `max(lambda * (T - T0), 0)` with `T0` the free-flow time of the same traversal; the excess ratio is excess over consumption.
				- Mobility production is the road space-time supplied: length × lanes × time. Utilisation is consumption over production. Consumption is scaled to the full population by the sample size; production is not.
				- Time bins are 15 minutes unless configured otherwise. The wide tables behind the animated maps have one column per bin.
				""";
		});
	}
}

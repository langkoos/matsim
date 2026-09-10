package org.matsim.contrib.mobilityconsumption.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.io.DatumWriter;
import org.apache.avro.specific.SpecificDatumWriter;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.avro.XYTData;
import org.matsim.contrib.mobilityconsumption.core.BinTotals;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionAccumulator;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionParameters;
import org.matsim.core.utils.io.IOUtils;

/**
 * Rasterises per-link, per-bin consumption onto a square grid and writes it as the XYT avro format that
 * SimWrapper's grid map animates over time. A link's consumption in a bin is spread evenly over the cells its
 * straight line from-node to to-node passes through, sampled every half cell.
 */
public final class GridRasterWriter {

	public static final String CONSUMPTION_KEY = "mc_kmh";
	public static final String EXCESS_KEY = "mc_excess_kmh";

	private final Network network;
	private final double cellSize;
	private final String crs;

	public GridRasterWriter(Network network, double cellSize, String crs) {
		if (cellSize <= 0) {
			throw new IllegalArgumentException("cellSize must be positive");
		}
		this.network = network;
		this.cellSize = cellSize;
		this.crs = crs;
	}

	/** Grid extent from the network's node coordinates. */
	record Extent(double minX, double minY, int nx, int ny) {
	}

	Extent extent() {
		double minX = Double.POSITIVE_INFINITY;
		double minY = Double.POSITIVE_INFINITY;
		double maxX = Double.NEGATIVE_INFINITY;
		double maxY = Double.NEGATIVE_INFINITY;
		for (var node : network.getNodes().values()) {
			Coord c = node.getCoord();
			minX = Math.min(minX, c.getX());
			minY = Math.min(minY, c.getY());
			maxX = Math.max(maxX, c.getX());
			maxY = Math.max(maxY, c.getY());
		}
		int nx = (int) Math.floor((maxX - minX) / cellSize) + 1;
		int ny = (int) Math.floor((maxY - minY) / cellSize) + 1;
		return new Extent(minX, minY, nx, ny);
	}

	/** Cell weights along a link: cell index (xi * ny + yi) to share of the link in that cell. */
	Map<Integer, Double> cellShares(Link link, Extent ext) {
		Coord from = link.getFromNode().getCoord();
		Coord to = link.getToNode().getCoord();
		double dx = to.getX() - from.getX();
		double dy = to.getY() - from.getY();
		double length = Math.hypot(dx, dy);
		int samples = Math.max(1, (int) Math.ceil(length / (cellSize / 2)));
		Map<Integer, Double> shares = new HashMap<>();
		for (int s = 0; s < samples; s++) {
			double t = samples == 1 ? 0.5 : (s + 0.5) / samples;
			double x = from.getX() + t * dx;
			double y = from.getY() + t * dy;
			int xi = Math.min(ext.nx() - 1, Math.max(0, (int) Math.floor((x - ext.minX()) / cellSize)));
			int yi = Math.min(ext.ny() - 1, Math.max(0, (int) Math.floor((y - ext.minY()) / cellSize)));
			shares.merge(xi * ext.ny() + yi, 1.0 / samples, Double::sum);
		}
		return shares;
	}

	public void write(String path, MobilityConsumptionAccumulator acc) {
		MobilityConsumptionParameters p = acc.getParameters();
		Extent ext = extent();
		int bins = p.binCount();
		double[] consumption = new double[bins * ext.nx() * ext.ny()];
		double[] excess = new double[consumption.length];
		double up = p.upscaleFactor();
		for (Map.Entry<Id<Link>, BinTotals> e : acc.links().entrySet()) {
			Link link = network.getLinks().get(e.getKey());
			if (link == null) {
				continue;
			}
			BinTotals t = e.getValue();
			for (Map.Entry<Integer, Double> cell : cellShares(link, ext).entrySet()) {
				for (int b = 0; b < bins; b++) {
					int index = b * ext.nx() * ext.ny() + cell.getKey();
					consumption[index] += cell.getValue() * up * t.consumption(b);
					excess[index] += cell.getValue() * up * t.excess(b);
				}
			}
		}
		XYTData data = new XYTData();
		data.setCrs(crs);
		List<Float> xs = new ArrayList<>();
		for (int xi = 0; xi < ext.nx(); xi++) {
			xs.add((float) (ext.minX() + (xi + 0.5) * cellSize));
		}
		List<Float> ys = new ArrayList<>();
		for (int yi = 0; yi < ext.ny(); yi++) {
			ys.add((float) (ext.minY() + (yi + 0.5) * cellSize));
		}
		List<Integer> times = new ArrayList<>();
		for (int b = 0; b < bins; b++) {
			times.add((int) p.binStart(b));
		}
		data.setXCoords(xs);
		data.setYCoords(ys);
		data.setTimestamps(times);
		Map<CharSequence, List<Float>> values = new HashMap<>();
		values.put(CONSUMPTION_KEY, toKmh(consumption));
		values.put(EXCESS_KEY, toKmh(excess));
		data.setData(values);
		DatumWriter<XYTData> datumWriter = new SpecificDatumWriter<>(XYTData.class);
		try (DataFileWriter<XYTData> fileWriter = new DataFileWriter<>(datumWriter)) {
			fileWriter.setCodec(CodecFactory.deflateCodec(9));
			fileWriter.create(data.getSchema(), IOUtils.getOutputStream(IOUtils.getFileUrl(path), false));
			fileWriter.append(data);
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static List<Float> toKmh(double[] metreSeconds) {
		List<Float> out = new ArrayList<>(metreSeconds.length);
		for (double v : metreSeconds) {
			out.add((float) MobilityConsumptionParameters.toKilometreHours(v));
		}
		return out;
	}
}

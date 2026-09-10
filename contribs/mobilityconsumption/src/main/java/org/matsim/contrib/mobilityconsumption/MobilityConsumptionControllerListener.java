package org.matsim.contrib.mobilityconsumption;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import org.apache.commons.csv.CSVPrinter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.mobilityconsumption.core.MobilityConsumptionAccumulator;
import org.matsim.contrib.mobilityconsumption.core.MobilityProduction;
import org.matsim.contrib.mobilityconsumption.core.TraversalSegment;
import org.matsim.contrib.mobilityconsumption.io.MobilityConsumptionWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.ControllerConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.ShutdownListener;

import com.google.inject.Inject;

/**
 * Receives segments from the collector, feeds the accumulator, and writes the outputs: a stats row every
 * iteration, the per-link and per-bin tables in written iterations, and {@code output_} copies at shutdown.
 */
public final class MobilityConsumptionControllerListener implements IterationStartsListener, IterationEndsListener,
	ShutdownListener, Consumer<TraversalSegment> {

	public static final String LINKS_FILE = "mobilityConsumption_links.csv";
	public static final String LINK_BINS_FILE = "mobilityConsumption_links_bins.csv";
	public static final String NETWORK_BINS_FILE = "mobilityConsumption_network_bins.csv";
	public static final String SEGMENTS_FILE = "mobilityConsumption_segments.csv";
	public static final String STATS_FILE = "mobilityConsumption_stats.csv";

	private static final Logger log = LogManager.getLogger(MobilityConsumptionControllerListener.class);

	private final MobilityConsumptionConfigGroup configGroup;
	private final MobilityConsumptionAccumulator accumulator;
	private final Scenario scenario;
	private final OutputDirectoryHierarchy io;
	private final ControllerConfigGroup.CompressionType compression;
	private final MobilityConsumptionWriter writer;

	private MobilityProduction production;
	private CSVPrinter segmentPrinter;
	private boolean statsHeaderWritten;
	private int lastWrittenIteration = -1;

	@Inject
	public MobilityConsumptionControllerListener(Config config, MobilityConsumptionAccumulator accumulator,
			Scenario scenario, OutputDirectoryHierarchy io) {
		this.configGroup = (MobilityConsumptionConfigGroup) config.getModules().get(MobilityConsumptionConfigGroup.GROUP_NAME);
		this.accumulator = accumulator;
		this.scenario = scenario;
		this.io = io;
		this.compression = config.controller().getCompressionType();
		this.writer = new MobilityConsumptionWriter(config.global().getDefaultDelimiter());
	}

	public MobilityConsumptionAccumulator getAccumulator() {
		return accumulator;
	}

	/** Production is computed once; the network does not change between iterations. */
	public MobilityProduction getProduction() {
		if (production == null) {
			production = new MobilityProduction(scenario.getNetwork(), accumulator.getParameters(),
				configGroup.getNetworkModes());
		}
		return production;
	}

	boolean isWrittenIteration(int iteration, boolean last) {
		int interval = configGroup.getWriteInterval();
		return last || (interval > 0 && iteration % interval == 0);
	}

	@Override
	public void accept(TraversalSegment segment) {
		accumulator.add(segment);
		if (segmentPrinter != null) {
			try {
				MobilityConsumptionWriter.writeSegment(segmentPrinter, segment, accumulator.getCalculator());
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	@Override
	public void notifyIterationStarts(IterationStartsEvent event) {
		accumulator.reset();
		if (configGroup.isWriteSegments() && isWrittenIteration(event.getIteration(), event.isLastIteration())) {
			segmentPrinter = writer.open(io.getIterationFilename(event.getIteration(), SEGMENTS_FILE, compression));
			try {
				segmentPrinter.printRecord(MobilityConsumptionWriter.SEGMENT_HEADER);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	@Override
	public void notifyIterationEnds(IterationEndsEvent event) {
		closeSegmentPrinter();
		writeStatsRow(event.getIteration());
		if (isWrittenIteration(event.getIteration(), event.isLastIteration())) {
			int it = event.getIteration();
			writer.writeLinks(io.getIterationFilename(it, LINKS_FILE, compression), accumulator, getProduction(),
				scenario.getNetwork());
			writer.writeLinkBins(io.getIterationFilename(it, LINK_BINS_FILE, compression), accumulator, getProduction());
			writer.writeNetworkBins(io.getIterationFilename(it, NETWORK_BINS_FILE), accumulator, getProduction());
			lastWrittenIteration = it;
			log.info("Mobility consumption of iteration {}: {}", it,
				MobilityConsumptionWriter.statsRow(it, accumulator, getProduction()));
		}
	}

	@Override
	public void notifyShutdown(ShutdownEvent event) {
		closeSegmentPrinter();
		if (lastWrittenIteration < 0) {
			return;
		}
		int it = lastWrittenIteration;
		copy(io.getIterationFilename(it, LINKS_FILE, compression), outputName(LINKS_FILE, compression));
		copy(io.getIterationFilename(it, LINK_BINS_FILE, compression), outputName(LINK_BINS_FILE, compression));
		copy(io.getIterationFilename(it, NETWORK_BINS_FILE), outputName(NETWORK_BINS_FILE, null));
		copy(io.getOutputFilename(STATS_FILE), outputName(STATS_FILE, null));
		if (configGroup.isWriteSegments()) {
			copy(io.getIterationFilename(it, SEGMENTS_FILE, compression), outputName(SEGMENTS_FILE, compression));
		}
	}

	private String outputName(String file, ControllerConfigGroup.CompressionType compressionType) {
		return io.getOutputFilenameWithOutputPrefix(file) + (compressionType == null ? "" : compressionType.fileEnding);
	}

	private void writeStatsRow(int iteration) {
		String path = io.getOutputFilename(STATS_FILE);
		try (CSVPrinter printer = statsHeaderWritten ? writer.openAppend(path) : writer.open(path)) {
			if (!statsHeaderWritten) {
				printer.printRecord(MobilityConsumptionWriter.STATS_HEADER);
				statsHeaderWritten = true;
			}
			printer.printRecord(MobilityConsumptionWriter.statsRow(iteration, accumulator, getProduction()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private void closeSegmentPrinter() {
		if (segmentPrinter != null) {
			try {
				segmentPrinter.close();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			segmentPrinter = null;
		}
	}

	private static void copy(String from, String to) {
		try {
			Files.copy(new File(from).toPath(), new File(to).toPath(), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}

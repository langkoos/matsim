/* *********************************************************************** *
 * project: org.matsim.*
 * QControler.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package playground.andreas.intersection;

import java.io.File;

import org.apache.log4j.Logger;
import org.matsim.config.Config;
import org.matsim.controler.Controler;
import org.matsim.gbl.Gbl;
import org.matsim.run.Events2Snapshot;
import org.matsim.utils.vis.netvis.NetVis;

import playground.andreas.intersection.sim.QSim;

public class QControler extends Controler {

	@SuppressWarnings("unused")
	final private static Logger log = Logger.getLogger(QControler.class);
	
	final String signalSystems = "./src/playground/andreas/intersection/test/data/signalSystemConfig_2a_alt.xml";
	final String groupDefinitions = "./src/playground/andreas/intersection/test/data/signalGroupDefinition_2a_alt.xml";
	final static boolean useOTF = false;

	public QControler(final Config config) {
		super(config);
	}

	@Override
	protected void runMobSim() {
		QSim sim = new QSim(this.events, this.population, this.network,
				this.signalSystems, this.groupDefinitions, useOTF);
		sim.run();
	}

	/** Conversion of events -> snapshots */
	protected void makeVis() {

		File driversLog = new File("./output/ITERS/it.0/0.events.txt.gz");
		File visDir = new File("./output/vis");
		File eventsFile = new File("./output/vis/events.txt.gz");

		if (driversLog.exists()) {
			visDir.mkdir();
			driversLog.renameTo(eventsFile);

			Events2Snapshot events2Snapshot = new org.matsim.run.Events2Snapshot();
			events2Snapshot.run(eventsFile, Gbl.getConfig(), this.network);

			// Run NetVis if possible
			if (Gbl.getConfig().getParam("simulation", "snapshotFormat").equalsIgnoreCase("netvis")) {
				String[] visargs = { "./output/vis/Snapshot" };
				NetVis.main(visargs);
			}

		} else {
			System.err.println("Couldn't find " + driversLog);
			System.exit(0);
		}

		String[] visargs = { "./output/ITERS/it.0/Snapshot" };
		NetVis.main(visargs);
	}

	public static void main(final String[] args) {

		Config config;

		if (args.length == 0) {
			config = Gbl.createConfig(new String[] { "./src/playground/andreas/intersection/config.xml" });
		} else {
			config = Gbl.createConfig(args);
		}

		final QControler controler = new QControler(config);
		controler.setOverwriteFiles(true);
		controler.setWriteEvents(true);

		controler.run();
//		controler.makeVis();
		
//		if (QControler.useOTF){
////			OTFVis.main(new String [] {"./output/ITERS/it.0/0.otfvis.mvi"});
//		} else {
//			NetVis.main(new String[]{"./output/ITERS/it.0/Snapshot"});
//		}
	}

}

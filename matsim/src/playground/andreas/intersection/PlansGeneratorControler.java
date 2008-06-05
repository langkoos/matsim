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

import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.matsim.basic.v01.IdImpl;
import org.matsim.config.Config;
import org.matsim.controler.Controler;
import org.matsim.gbl.Gbl;
import org.matsim.network.Link;
import org.matsim.plans.Person;
import org.matsim.plans.Plan;
import org.matsim.plans.Plans;
import org.matsim.utils.misc.Time;

public class PlansGeneratorControler extends Controler {

	final private static Logger log = Logger.getLogger(PlansGeneratorControler.class);

	public PlansGeneratorControler(final Config config) {
		super(config);
	}

	@Override
	protected void runMobSim() {		

	}

	/** Should be overwritten in case of artificial population */
	@Override
	protected Plans loadPopulation() {
		
		final int agentsPerDest = 1;
		int numberOfPlans = 1;

		Plans pop = new Plans(Plans.NO_STREAMING);
		log.info("  generating plans... ");
		
		LinkedList <Link> fromLinks = new LinkedList<Link>();
		LinkedList <Link> toLinks = new LinkedList<Link>();
		
		fromLinks.add(this.network.getLink("5"));
//		fromLinks.add(this.network.getLink("40"));
//		fromLinks.add(this.network.getLink("60"));
//		fromLinks.add(this.network.getLink("80"));
		
		toLinks.add(this.network.getLink("3"));
//		toLinks.add(this.network.getLink("201"));
//		toLinks.add(this.network.getLink("301"));
//		toLinks.add(this.network.getLink("401"));
		
		for(int i=0; i < 5000; i++){
			for (Link fromLink : fromLinks) {
				
				for (Link toLink : toLinks) {
					
					if (!fromLink.equals(toLink)){
					
						for (int ii = 1; ii <= agentsPerDest; ii++) {
							generatePerson(numberOfPlans, fromLink, toLink, pop);
							numberOfPlans++;
						}
					}
				}			
			}	
			
		}
		
		
		
//		generatePerson(numberOfPlans, this.network.getLink("30"), this.network.getLink("201"), pop);

		return pop;

	}

	/** Generates one Person a time */
	private void generatePerson(final int ii, final Link fromLink, final Link toLink, final Plans population) {
		Person p = new Person(new IdImpl(String.valueOf(ii)));
		Plan plan = new Plan(p);
		try {
			plan.createAct("h", 100., 100., fromLink, 0., 3 * 60 * 60., Time.UNDEFINED_TIME, true);
			plan.createLeg("car", null, null, null);
			plan.createAct("h", 200., 200., toLink, 8 * 60 * 60, 0., 0., true);

			p.addPlan(plan);
			population.addPerson(p);
		} catch (Exception e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}	

	public static void main(final String[] args) {

		Config config = Gbl.createConfig(new String[] { "./src/playground/andreas/intersection/config.xml" });
		
		String netFileName = "src/playground/andreas/intersection/test/data/net_2a.xml.gz";
		config.network().setInputFile(netFileName);
		
		final PlansGeneratorControler controler = new PlansGeneratorControler(config);
		controler.setOverwriteFiles(true);
		controler.setWriteEvents(true);

		controler.run();
	}

}

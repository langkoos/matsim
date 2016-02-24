/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
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

/**
 * 
 */
package org.matsim.contrib.accessibility.run;

import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.accessibility.utils.Distances;
import org.matsim.contrib.accessibility.utils.NetworkUtil;
import org.matsim.core.network.LinkImpl;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.testcases.MatsimTestUtils;

/**
 * @author dziemke
 *
 */
public class NetworkUtilTest {
	
	private static final Logger log = Logger.getLogger(NetworkUtilTest.class);
	
	@Rule public MatsimTestUtils utils = new MatsimTestUtils();
	
	
	@Test
	public void testNetworkUtil() {
		/* create a sample network:
		 *
		 *               (e)          (f)
		 *           (3)----3----(4)  (g)
		 *         /              /
		 *       2              /
		 *     /  (d)         /
		 * (2) (c)          4  (h)
		 *  |              /
		 *  1            /
		 *  |  (b)     /
		 * (1) (a)   (5) (i)
		 *
		 * The network contains an exactly horizontal, an exactly vertical, an exactly diagonal
		 * and another link with no special slope to also test possible special cases.
		 * 
		 * why is that a "special case"? in a normal network all sort of slopes are *normally* present. dz, feb'16
		 */
		
		NetworkImpl network = (NetworkImpl) NetworkUtils.createNetwork();
		Node node1 = network.createAndAddNode(Id.create("1", Node.class), new Coord((double) 0, (double) 0));
		Node node2 = network.createAndAddNode(Id.create("2", Node.class), new Coord((double) 0, (double) 1000));
		Node node3 = network.createAndAddNode(Id.create("3", Node.class), new Coord((double) 1000, (double) 2000));
		Node node4 = network.createAndAddNode(Id.create("4", Node.class), new Coord((double) 2000, (double) 2000));
		Node node5 = network.createAndAddNode(Id.create("5", Node.class), new Coord((double) 1000, (double) 0));
		LinkImpl link1 = (LinkImpl) network.createAndAddLink(Id.create("1", Link.class), node1, node2, 1000, 1, 3600, 1);
		LinkImpl link2 = (LinkImpl) network.createAndAddLink(Id.create("2", Link.class), node2, node3, 1500, 1, 3600, 1);
		LinkImpl link3 = (LinkImpl) network.createAndAddLink(Id.create("3", Link.class), node3, node4, 1000, 1, 3600, 1);
		LinkImpl link4 = (LinkImpl) network.createAndAddLink(Id.create("4", Link.class), node4, node5, 2800, 1, 3600, 1);
		
		Coord a = new Coord(100., 0.);
		Coord b = new Coord(100., 100.);
		Coord c = new Coord(100., 1000.);
		Coord d = new Coord(300., 1200.);
		Coord e = new Coord(1300, 2100.);
		Coord f = new Coord(2300., 2100.);
		Coord g = new Coord(2300., 2000.);
		Coord h = new Coord(1700., 1000.);
		Coord i = new Coord(0., 1200.);
		
		Distances distanceA11 = NetworkUtil.getDistances2NodeViaGivenLink(a, link1, node1);
		Assert.assertEquals("distanceA11.getDistancePoint2Road()", 100., distanceA11.getDistancePoint2Road(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("distanceA11.getDistanceRoad2Node()", 0., distanceA11.getDistanceRoad2Node(), MatsimTestUtils.EPSILON);
		
		Coord projectionA11 = NetworkUtil.getInteresectionOfProjection(a, link1, node1);
		Assert.assertEquals("projectionA11.getX()", 0., projectionA11.getX(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("projectionA11.getY()", 0., projectionA11.getY(), MatsimTestUtils.EPSILON);
		
		
		Distances distanceB11 = NetworkUtil.getDistances2NodeViaGivenLink(b, link1, node1);
		Assert.assertEquals("distanceB11.getDistancePoint2Road()", 100., distanceB11.getDistancePoint2Road(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("distanceB11.getDistanceRoad2Node()", 100., distanceB11.getDistanceRoad2Node(), MatsimTestUtils.EPSILON);
		
		Coord projectionB11 = NetworkUtil.getInteresectionOfProjection(b, link1, node1);
		Assert.assertEquals("projectionB11.getX()", 0., projectionB11.getX(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("projectionB11.getY()", 100., projectionB11.getY(), MatsimTestUtils.EPSILON);
		
		
		Distances distanceB12 = NetworkUtil.getDistances2NodeViaGivenLink(b, link1, node2);
		Assert.assertEquals("distanceB12.getDistancePoint2Road()", 100., distanceB12.getDistancePoint2Road(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("distanceB12.getDistanceRoad2Node()", 900., distanceB12.getDistanceRoad2Node(), MatsimTestUtils.EPSILON);
		
		Coord projectionB12 = NetworkUtil.getInteresectionOfProjection(b, link1, node2);
		Assert.assertEquals("projectionB12.getX()", 0., projectionB12.getX(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("projectionB12.getY()", 100., projectionB12.getY(), MatsimTestUtils.EPSILON);
		
		
		Distances distanceC11 = NetworkUtil.getDistances2NodeViaGivenLink(c, link1, node1);
		Assert.assertEquals("distanceC11.getDistancePoint2Road()", 100., distanceC11.getDistancePoint2Road(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("distanceC11.getDistanceRoad2Node()", 1000., distanceC11.getDistanceRoad2Node(), MatsimTestUtils.EPSILON);
		
		Coord projectionC11 = NetworkUtil.getInteresectionOfProjection(c, link1, node1);
		Assert.assertEquals("projectionC11.getX()", 0., projectionC11.getX(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("projectionC11.getY()", 1000., projectionC11.getY(), MatsimTestUtils.EPSILON);
		
		
		Distances distanceC12 = NetworkUtil.getDistances2NodeViaGivenLink(c, link1, node2);
		Assert.assertEquals("distanceC12.getDistancePoint2Road()", 100., distanceC12.getDistancePoint2Road(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("distanceC12.getDistanceRoad2Node()", 0., distanceC12.getDistanceRoad2Node(), MatsimTestUtils.EPSILON);
		
		Coord projectionC12 = NetworkUtil.getInteresectionOfProjection(c, link1, node2);
		Assert.assertEquals("projectionC12.getX()", 0., projectionC12.getX(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("projectionC12.getY()", 1000., projectionC12.getY(), MatsimTestUtils.EPSILON);
		
		
		Distances distanceC22 = NetworkUtil.getDistances2NodeViaGivenLink(c, link2, node2);
		Assert.assertEquals("distanceC22.getDistancePoint2Road()", Math.sqrt(2.) / 2. * 100., distanceC22.getDistancePoint2Road(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("distanceC22.getDistanceRoad2Node()", Math.sqrt(2.) / 2. * 100., distanceC22.getDistanceRoad2Node(), MatsimTestUtils.EPSILON);
		
		Coord projectionC22 = NetworkUtil.getInteresectionOfProjection(c, link2, node2);
		Assert.assertEquals("projectionC22.getX()", 50., projectionC22.getX(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("projectionC22.getY()", 1050., projectionC22.getY(), MatsimTestUtils.EPSILON);
		
		
		Distances distanceD22 = NetworkUtil.getDistances2NodeViaGivenLink(d, link2, node2);
		Assert.assertEquals("distanceD22.getDistancePoint2Road()", Math.sqrt(2.) / 2. * 100.0, distanceD22.getDistancePoint2Road(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("distanceD22.getDistanceRoad2Node()", Math.sqrt(2.) / 2. * 100.0 + Math.sqrt(200*200 + 200*200), distanceD22.getDistanceRoad2Node(), MatsimTestUtils.EPSILON);
		
		Coord projectionD22 = NetworkUtil.getInteresectionOfProjection(d, link2, node2);
		Assert.assertEquals("projectionD22.getX()", 250., projectionD22.getX(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("projectionD22.getY()", 1250., projectionD22.getY(), MatsimTestUtils.EPSILON);
		
		
		Distances distanceD23 = NetworkUtil.getDistances2NodeViaGivenLink(d, link2, node3);
		Assert.assertEquals("distanceD23.getDistancePoint2Road()", Math.sqrt(2.)/2.*100.0, distanceD23.getDistancePoint2Road(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("distanceD23.getDistanceRoad2Node()", Math.sqrt(2.)/2.*100.0 + Math.sqrt(700*700+700*700), distanceD23.getDistanceRoad2Node(), MatsimTestUtils.EPSILON);

		Coord projectionD23 = NetworkUtil.getInteresectionOfProjection(d, link2, node3);
		Assert.assertEquals("projectionD23.getX()", 250., projectionD23.getX(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("projectionD23.getY()", 1250., projectionD23.getY(), MatsimTestUtils.EPSILON);
		
		
//		Distances distanceE33 = NetworkUtil.getDistances2NodeViaGivenLink(e, link3, node3);
//		Assert.assertEquals("distanceE33.getDistancePoint2Road()", 100.0, distanceE33.getDistancePoint2Road(), MatsimTestUtils.EPSILON);
//		Assert.assertEquals("distanceE33.getDistanceRoad2Node()", 300.0, distanceE33.getDistanceRoad2Node(), MatsimTestUtils.EPSILON);
		
		Coord projectionE33 = NetworkUtil.getInteresectionOfProjection(e, link3, node3);
		Assert.assertEquals("projectionE33.getX()", 1300., projectionE33.getX(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("projectionE33.getY()", 2000., projectionE33.getY(), MatsimTestUtils.EPSILON);
		
//		
//		Distances distanceE34 = NetworkUtil.getDistances2NodeViaGivenLink(e, link3, node4);
//		Assert.assertEquals("distanceE34.getDistancePoint2Road()", 100.0, distanceE34.getDistancePoint2Road(), MatsimTestUtils.EPSILON);
//		Assert.assertEquals("distanceE34.getDistanceRoad2Node()", 700.0, distanceE34.getDistanceRoad2Node(), MatsimTestUtils.EPSILON);
		
		Coord projectionE34 = NetworkUtil.getInteresectionOfProjection(e, link3, node4);
		Assert.assertEquals("projectionE34.getX()", 1300., projectionE34.getX(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("projectionE34.getY()", 2000., projectionE34.getY(), MatsimTestUtils.EPSILON);
		
		
		// from Coord F on only checking for correct projections as I will remove the distance calculation eventually anyways
		Coord projectionF33 = NetworkUtil.getInteresectionOfProjection(f, link3, node3);
//		Assert.assertEquals("projectionF33.getX()", 2000., projectionF33.getX(), MatsimTestUtils.EPSILON); // TODO
		Assert.assertEquals("projectionF33.getY()", 2000., projectionF33.getY(), MatsimTestUtils.EPSILON);
		
		Coord projectionF33_2 = CoordUtils.orthogonalProjectionOnLineSegment(link3.getFromNode().getCoord(), link3.getToNode().getCoord(), f);
		Assert.assertEquals("projectionF33_2.getX()", 2000., projectionF33_2.getX(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("projectionF33_2.getY()", 2000., projectionF33_2.getY(), MatsimTestUtils.EPSILON);
		
		Coord projectionF34 = NetworkUtil.getInteresectionOfProjection(f, link3, node4);
//		Assert.assertEquals("projectionF34.getX()", 2000., projectionF34.getX(), MatsimTestUtils.EPSILON);
		Assert.assertEquals("projectionF34.getY()", 2000., projectionF34.getY(), MatsimTestUtils.EPSILON);
	}
}
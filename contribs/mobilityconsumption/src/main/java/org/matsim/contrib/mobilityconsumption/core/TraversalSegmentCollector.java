package org.matsim.contrib.mobilityconsumption.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleAbortsEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleAbortsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;

/**
 * Turns the event stream into {@link TraversalSegment}s.
 *
 * <p>MATSim's queue simulation emits, for a leg over links l0, l1, l2: vehicle-enters-traffic on l0 at relative
 * position 1.0, link-leave l0, link-enter l1, link-leave l1, link-enter l2, vehicle-leaves-traffic on l2 at
 * position 1.0. The first pair is a {@link SegmentKind#DEPARTURE} segment (zero distance, buffer waiting), the
 * middle pair a {@link SegmentKind#FULL} traversal, the last pair an {@link SegmentKind#ARRIVAL} segment.
 *
 * <p>Free-flow time follows the QSim: {@code step * floor(L / min(freespeed(t), vmax) / step)}, plus one time step
 * for a full traversal because the node move takes a step, without it for an arrival because the vehicle leaves
 * traffic at its earliest exit time. So an uncongested run reports zero excess.
 */
public final class TraversalSegmentCollector implements VehicleEntersTrafficEventHandler,
	VehicleLeavesTrafficEventHandler, LinkEnterEventHandler, LinkLeaveEventHandler, VehicleAbortsEventHandler,
	TransitDriverStartsEventHandler {

	private final Network network;
	private final MobilityConsumptionParameters parameters;
	private final CollectorSettings settings;
	private final Function<Id<Vehicle>, VehicleType> vehicleTypes;
	private final Consumer<TraversalSegment> sink;
	private final Runnable abortSink;

	private final Map<Id<Vehicle>, Leg> legs = new HashMap<>();
	private final Map<Id<Vehicle>, Open> open = new HashMap<>();
	private final Set<Id<Vehicle>> transitVehicles = new HashSet<>();

	private record Leg(Id<Person> driverId, String mode, double spaceOccupied, double maxVelocity) {
	}

	private record Open(Id<Link> linkId, double enterTime, SegmentKind kind) {
	}

	/**
	 * @param vehicleTypes lookup of a vehicle's type, may return null; only consulted when the settings ask for
	 *                     vehicle-type lengths or a maximum velocity
	 * @param sink         receives every completed segment
	 * @param abortSink    called once for every segment cut short by a vehicle abort
	 */
	public TraversalSegmentCollector(Network network, MobilityConsumptionParameters parameters,
			CollectorSettings settings, Function<Id<Vehicle>, VehicleType> vehicleTypes,
			Consumer<TraversalSegment> sink, Runnable abortSink) {
		this.network = network;
		this.parameters = parameters;
		this.settings = settings;
		this.vehicleTypes = vehicleTypes;
		this.sink = sink;
		this.abortSink = abortSink;
	}

	/** Convenience constructor feeding an accumulator. */
	public TraversalSegmentCollector(Network network, CollectorSettings settings,
			Function<Id<Vehicle>, VehicleType> vehicleTypes, MobilityConsumptionAccumulator accumulator) {
		this(network, accumulator.getParameters(), settings, vehicleTypes, accumulator::add, accumulator::recordAborted);
	}

	@Override
	public void reset(int iteration) {
		legs.clear();
		open.clear();
		transitVehicles.clear();
	}

	/** Number of vehicles currently between vehicle-enters-traffic and vehicle-leaves-traffic. */
	public int vehiclesInTraffic() {
		return legs.size();
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		if (settings.excludeTransitVehicles()) {
			transitVehicles.add(event.getVehicleId());
		}
	}

	@Override
	public void handleEvent(VehicleEntersTrafficEvent event) {
		Id<Vehicle> vehicleId = event.getVehicleId();
		if (!settings.networkModes().contains(event.getNetworkMode()) || transitVehicles.contains(vehicleId)) {
			legs.remove(vehicleId);
			open.remove(vehicleId);
			return;
		}
		VehicleType type = vehicleTypes.apply(vehicleId);
		double space = parameters.lambda();
		if (settings.vehicleLengthSource() == VehicleLengthSource.vehicleType && type != null) {
			space = type.getLength();
		}
		double maxVelocity = type == null ? Double.POSITIVE_INFINITY : type.getMaximumVelocity();
		legs.put(vehicleId, new Leg(event.getPersonId(), event.getNetworkMode(), space, maxVelocity));
		open.put(vehicleId, new Open(event.getLinkId(), event.getTime(), SegmentKind.DEPARTURE));
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		if (legs.containsKey(event.getVehicleId())) {
			open.put(event.getVehicleId(), new Open(event.getLinkId(), event.getTime(), SegmentKind.FULL));
		}
	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {
		Open o = open.remove(event.getVehicleId());
		Leg leg = legs.get(event.getVehicleId());
		if (o == null || leg == null || !o.linkId().equals(event.getLinkId())) {
			return;
		}
		if (o.kind() == SegmentKind.DEPARTURE && !settings.includeDepartureSegments()) {
			return;
		}
		Link link = network.getLinks().get(event.getLinkId());
		double distance = o.kind() == SegmentKind.DEPARTURE ? 0 : link.getLength();
		double freeFlow = o.kind() == SegmentKind.DEPARTURE ? 0
			: freeFlowTime(link, o.enterTime(), leg.maxVelocity()) + settings.timeStepSize();
		emit(o, leg, event.getVehicleId(), event.getTime(), distance, freeFlow, o.kind());
	}

	@Override
	public void handleEvent(VehicleLeavesTrafficEvent event) {
		Open o = open.remove(event.getVehicleId());
		Leg leg = legs.remove(event.getVehicleId());
		if (o == null || leg == null || !o.linkId().equals(event.getLinkId())) {
			return;
		}
		if (o.kind() == SegmentKind.DEPARTURE) {
			// Entered and left traffic on the same link: only buffer waiting, no distance.
			if (settings.includeDepartureSegments()) {
				emit(o, leg, event.getVehicleId(), event.getTime(), 0, 0, SegmentKind.DEPARTURE);
			}
			return;
		}
		if (!settings.includeArrivalSegments()) {
			return;
		}
		Link link = network.getLinks().get(event.getLinkId());
		double distance = link.getLength() * event.getRelativePositionOnLink();
		double freeFlow = freeFlowTime(link, o.enterTime(), leg.maxVelocity()) * event.getRelativePositionOnLink();
		emit(o, leg, event.getVehicleId(), event.getTime(), distance, freeFlow, SegmentKind.ARRIVAL);
	}

	@Override
	public void handleEvent(VehicleAbortsEvent event) {
		if (open.remove(event.getVehicleId()) != null) {
			abortSink.run();
		}
		legs.remove(event.getVehicleId());
	}

	private void emit(Open o, Leg leg, Id<Vehicle> vehicleId, double leaveTime, double distance, double freeFlow,
			SegmentKind kind) {
		sink.accept(new TraversalSegment(o.linkId(), vehicleId, leg.driverId(), leg.mode(), kind, o.enterTime(),
			leaveTime, distance, freeFlow, leg.spaceOccupied()));
	}

	/** QSim free-flow time for the whole link, without the node step. */
	double freeFlowTime(Link link, double time, double maxVelocity) {
		double speed = Math.min(link.getFreespeed(time), maxVelocity);
		double step = settings.timeStepSize();
		return step * Math.floor(link.getLength() / speed / step);
	}
}

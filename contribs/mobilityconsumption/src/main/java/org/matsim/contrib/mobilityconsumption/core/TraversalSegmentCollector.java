package org.matsim.contrib.mobilityconsumption.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.LinkLeaveEvent;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.VehicleAbortsEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.LinkEnterEventHandler;
import org.matsim.api.core.v01.events.handler.LinkLeaveEventHandler;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleAbortsEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
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
 *
 * <p>Occupancy is tracked from person-enters-vehicle and person-leaves-vehicle events as a time integral, so a
 * segment carries the mean number of people aboard while it lasted. Transit vehicles (seen in a
 * transit-driver-starts event) are excluded by default; when included they are tagged
 * {@link ConsumptionSource#TRANSIT}, their driver does not count as an occupant, their mode is reported as
 * {@code pt:<vehicle type>}, and the time they dwell at stops counts as scheduled, not excess.
 */
public final class TraversalSegmentCollector implements VehicleEntersTrafficEventHandler,
	VehicleLeavesTrafficEventHandler, LinkEnterEventHandler, LinkLeaveEventHandler, VehicleAbortsEventHandler,
	TransitDriverStartsEventHandler, PersonEntersVehicleEventHandler, PersonLeavesVehicleEventHandler,
	VehicleArrivesAtFacilityEventHandler, VehicleDepartsAtFacilityEventHandler {

	private final Network network;
	private final MobilityConsumptionParameters parameters;
	private final CollectorSettings settings;
	private final Function<Id<Vehicle>, VehicleType> vehicleTypes;
	private final Consumer<TraversalSegment> sink;
	private final Runnable abortSink;

	private final Map<Id<Vehicle>, Leg> legs = new HashMap<>();
	private final Map<Id<Vehicle>, Open> open = new HashMap<>();
	private final Map<Id<Vehicle>, Id<Person>> transitDrivers = new HashMap<>();
	private final Map<Id<Vehicle>, Occupancy> occupancies = new HashMap<>();

	private record Leg(Id<Person> driverId, String mode, double spaceOccupied, double maxVelocity, ConsumptionSource source) {
	}

	private static final class Open {
		final Id<Link> linkId;
		final double enterTime;
		final SegmentKind kind;
		final double occupantSecondsAtStart;
		double dwellSeconds;
		double dwellStart = Double.NaN;

		Open(Id<Link> linkId, double enterTime, SegmentKind kind, double occupantSecondsAtStart) {
			this.linkId = linkId;
			this.enterTime = enterTime;
			this.kind = kind;
			this.occupantSecondsAtStart = occupantSecondsAtStart;
		}
	}

	/** Time integral of the people aboard a vehicle. */
	private static final class Occupancy {
		final Set<Id<Person>> aboard = new HashSet<>();
		double lastChange;
		double occupantSeconds;

		void update(double now) {
			occupantSeconds += aboard.size() * (now - lastChange);
			lastChange = now;
		}

		double integralAt(double now) {
			return occupantSeconds + aboard.size() * (now - lastChange);
		}
	}

	/**
	 * @param vehicleTypes lookup of a vehicle's type, may return null; consulted for maximum velocity and, depending
	 *                     on the settings, for the road space a vehicle occupies
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
		transitDrivers.clear();
		occupancies.clear();
	}

	/** Number of vehicles currently between vehicle-enters-traffic and vehicle-leaves-traffic. */
	public int vehiclesInTraffic() {
		return legs.size();
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		transitDrivers.put(event.getVehicleId(), event.getDriverId());
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		Id<Person> driver = transitDrivers.get(event.getVehicleId());
		if (driver != null && driver.equals(event.getPersonId())) {
			return;
		}
		Occupancy occ = occupancies.computeIfAbsent(event.getVehicleId(), id -> new Occupancy());
		occ.update(event.getTime());
		occ.aboard.add(event.getPersonId());
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		Occupancy occ = occupancies.get(event.getVehicleId());
		if (occ == null) {
			return;
		}
		occ.update(event.getTime());
		occ.aboard.remove(event.getPersonId());
		if (occ.aboard.isEmpty() && !legs.containsKey(event.getVehicleId())) {
			occupancies.remove(event.getVehicleId());
		}
	}

	@Override
	public void handleEvent(VehicleArrivesAtFacilityEvent event) {
		Open o = open.get(event.getVehicleId());
		if (o != null) {
			o.dwellStart = event.getTime();
		}
	}

	@Override
	public void handleEvent(VehicleDepartsAtFacilityEvent event) {
		Open o = open.get(event.getVehicleId());
		if (o != null && !Double.isNaN(o.dwellStart)) {
			o.dwellSeconds += event.getTime() - o.dwellStart;
			o.dwellStart = Double.NaN;
		}
	}

	@Override
	public void handleEvent(VehicleEntersTrafficEvent event) {
		Id<Vehicle> vehicleId = event.getVehicleId();
		String mode = event.getNetworkMode();
		boolean transit = transitDrivers.containsKey(vehicleId);
		if (mode == null || !settings.networkModes().contains(mode) || (transit && settings.excludeTransitVehicles())) {
			legs.remove(vehicleId);
			open.remove(vehicleId);
			return;
		}
		VehicleType type = vehicleTypes.apply(vehicleId);
		double space = spaceOccupied(type);
		double maxVelocity = type == null ? Double.POSITIVE_INFINITY : type.getMaximumVelocity();
		ConsumptionSource source = transit ? ConsumptionSource.TRANSIT : ConsumptionSource.FLOW;
		String label = transit ? "pt:" + (type == null ? "unknown" : type.getId().toString()) : mode;
		legs.put(vehicleId, new Leg(event.getPersonId(), label, space, maxVelocity, source));
		open.put(vehicleId, new Open(event.getLinkId(), event.getTime(), SegmentKind.DEPARTURE, occupantSeconds(vehicleId, event.getTime())));
	}

	private double spaceOccupied(VehicleType type) {
		if (type != null) {
			Double explicit = settings.spaceByVehicleType().get(type.getId().toString());
			if (explicit != null) {
				return explicit;
			}
			if (settings.vehicleLengthSource() == VehicleLengthSource.vehicleType) {
				return type.getLength();
			}
		}
		return parameters.lambda();
	}

	@Override
	public void handleEvent(LinkEnterEvent event) {
		if (legs.containsKey(event.getVehicleId())) {
			open.put(event.getVehicleId(), new Open(event.getLinkId(), event.getTime(), SegmentKind.FULL,
				occupantSeconds(event.getVehicleId(), event.getTime())));
		}
	}

	@Override
	public void handleEvent(LinkLeaveEvent event) {
		Open o = open.remove(event.getVehicleId());
		if (o == null || !o.linkId.equals(event.getLinkId())) {
			return;
		}
		// An open segment always belongs to a tracked leg: both are removed together.
		Leg leg = Objects.requireNonNull(legs.get(event.getVehicleId()));
		if (o.kind == SegmentKind.DEPARTURE && !settings.includeDepartureSegments()) {
			return;
		}
		Link link = network.getLinks().get(event.getLinkId());
		double distance = o.kind == SegmentKind.DEPARTURE ? 0 : link.getLength();
		double freeFlow = o.kind == SegmentKind.DEPARTURE ? 0
			: freeFlowTime(link, o.enterTime, leg.maxVelocity()) + settings.timeStepSize();
		emit(o, leg, event.getVehicleId(), event.getTime(), distance, freeFlow, o.kind);
	}

	@Override
	public void handleEvent(VehicleLeavesTrafficEvent event) {
		Open o = open.remove(event.getVehicleId());
		Leg leg = legs.remove(event.getVehicleId());
		if (o == null || !o.linkId.equals(event.getLinkId())) {
			return;
		}
		Objects.requireNonNull(leg, "open segment without a tracked leg");
		if (o.kind == SegmentKind.DEPARTURE) {
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
		double freeFlow = freeFlowTime(link, o.enterTime, leg.maxVelocity()) * event.getRelativePositionOnLink();
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
		double duration = leaveTime - o.enterTime;
		double occupancy = duration > 0
			? (occupantSeconds(vehicleId, leaveTime) - o.occupantSecondsAtStart) / duration
			: occupantsNow(vehicleId);
		// Dwell at a stop that is still open when the segment ends counts up to the segment end.
		double dwell = o.dwellSeconds + (Double.isNaN(o.dwellStart) ? 0 : leaveTime - o.dwellStart);
		sink.accept(new TraversalSegment(o.linkId, vehicleId, leg.driverId(), leg.mode(), kind, o.enterTime,
			leaveTime, distance, freeFlow + dwell, leg.spaceOccupied(), leg.source(), occupancy));
	}

	private double occupantSeconds(Id<Vehicle> vehicleId, double now) {
		Occupancy occ = occupancies.get(vehicleId);
		return occ == null ? 0 : occ.integralAt(now);
	}

	private int occupantsNow(Id<Vehicle> vehicleId) {
		Occupancy occ = occupancies.get(vehicleId);
		return occ == null ? 0 : occ.aboard.size();
	}

	/** QSim free-flow time for the whole link, without the node step. */
	double freeFlowTime(Link link, double time, double maxVelocity) {
		double speed = Math.min(link.getFreespeed(time), maxVelocity);
		double step = settings.timeStepSize();
		return step * Math.floor(link.getLength() / speed / step);
	}
}

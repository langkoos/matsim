package org.matsim.contrib.mobilityconsumption.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.vehicles.Vehicle;

class TraversalSegmentTest {

	@Test
	void rejectsInconsistentValues() {
		Id<Vehicle> v = Id.create("v", Vehicle.class);
		assertThatThrownBy(() -> new TraversalSegment(Id.createLinkId("l"), v, null, "car", SegmentKind.FULL, 10, 5, 1, 1, 1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TraversalSegment(Id.createLinkId("l"), v, null, "car", SegmentKind.FULL, 0, 5, -1, 1, 1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TraversalSegment(Id.createLinkId("l"), v, null, "car", SegmentKind.FULL, 0, 5, 1, -1, 1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new TraversalSegment(Id.createLinkId("l"), v, null, "car", SegmentKind.FULL, 0, 5, 1, 1, -1))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new CollectorSettings(Set.of("car"), true, true, true, VehicleLengthSource.fixed, 0))
			.isInstanceOf(IllegalArgumentException.class);
	}
}

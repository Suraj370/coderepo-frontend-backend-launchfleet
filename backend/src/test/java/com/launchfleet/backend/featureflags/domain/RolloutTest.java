package com.launchfleet.backend.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class RolloutTest {

	@Test
	void rejectsEmptyAllocations() {
		assertThatThrownBy(() -> new Rollout(List.of())).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsDuplicateVariantIds() {
		assertThatThrownBy(() -> new Rollout(List.of(new Allocation("variant-1", 5000),
				new Allocation("variant-1", 5000)))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsTotalsNotEqualToTenThousand() {
		assertThatThrownBy(() -> new Rollout(List.of(new Allocation("variant-1", 4000),
				new Allocation("variant-2", 4000)))).isInstanceOf(IllegalArgumentException.class);

		assertThatThrownBy(() -> new Rollout(List.of(new Allocation("variant-1", 6000),
				new Allocation("variant-2", 6000)))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void acceptsATotalOfExactlyTenThousand() {
		Rollout rollout = new Rollout(List.of(new Allocation("variant-1", 3000), new Allocation("variant-2", 7000)));

		assertThat(rollout.allocations()).hasSize(2);
	}

	@Test
	void acceptsAZeroPercentAllocationAlongsideOthers() {
		Rollout rollout = new Rollout(
				List.of(new Allocation("variant-1", 0), new Allocation("variant-2", 10000)));

		assertThat(rollout.allocations()).extracting(Allocation::percentage).containsExactly(0, 10000);
	}

	@Test
	void sortedByVariantIdIsIndependentOfConstructionOrder() {
		Rollout rollout = new Rollout(List.of(new Allocation("zeta", 4000), new Allocation("alpha", 6000)));

		assertThat(rollout.sortedByVariantId()).extracting(Allocation::variantId).containsExactly("alpha", "zeta");
	}
}

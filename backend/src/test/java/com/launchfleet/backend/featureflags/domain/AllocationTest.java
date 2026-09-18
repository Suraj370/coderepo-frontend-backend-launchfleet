package com.launchfleet.backend.featureflags.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AllocationTest {

	@Test
	void rejectsNegativePercentage() {
		assertThatThrownBy(() -> new Allocation("variant-1", -1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsPercentageAboveTenThousand() {
		assertThatThrownBy(() -> new Allocation("variant-1", 10001)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void allowsZeroPercent() {
		Allocation allocation = new Allocation("variant-1", 0);

		assertThat(allocation.percentage()).isZero();
	}

	@Test
	void allowsExactlyTenThousand() {
		Allocation allocation = new Allocation("variant-1", 10000);

		assertThat(allocation.percentage()).isEqualTo(10000);
	}

	@Test
	void rejectsBlankVariantId() {
		assertThatThrownBy(() -> new Allocation(" ", 100)).isInstanceOf(IllegalArgumentException.class);
	}
}

package com.launchfleet.backend.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * A plain (non-Spring) unit test of the destructive-seed guard: main() calls
 * System.exit() on refusal, so the guard's truth table is exercised directly on the
 * extracted predicate instead - no JVM process/Spring context needed to prove that
 * BOTH the 'dev' profile and SEED_DATABASE=true are required, not either alone.
 */
class SeedRunnerGuardTest {

	@Test
	void requiresBothTheDevProfileAndTheExplicitSeedFlag() {
		assertThat(SeedRunner.isDestructiveSeedAllowed("dev", "true")).isTrue();
	}

	@Test
	void devProfileAloneIsNotEnough() {
		assertThat(SeedRunner.isDestructiveSeedAllowed("dev", null)).isFalse();
		assertThat(SeedRunner.isDestructiveSeedAllowed("dev", "false")).isFalse();
	}

	@Test
	void seedFlagAloneIsNotEnough() {
		assertThat(SeedRunner.isDestructiveSeedAllowed(null, "true")).isFalse();
		assertThat(SeedRunner.isDestructiveSeedAllowed("production", "true")).isFalse();
	}

	@Test
	void neitherIsNotEnough() {
		assertThat(SeedRunner.isDestructiveSeedAllowed(null, null)).isFalse();
		assertThat(SeedRunner.isDestructiveSeedAllowed("", "")).isFalse();
	}

	@Test
	void devProfileMayBeOneOfSeveralCommaSeparatedProfiles() {
		assertThat(SeedRunner.isDestructiveSeedAllowed("local,dev", "true")).isTrue();
	}
}

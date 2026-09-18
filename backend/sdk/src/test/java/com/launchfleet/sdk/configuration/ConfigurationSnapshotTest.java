package com.launchfleet.sdk.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.launchfleet.sdk.transport.SdkConfigurationWire;
import com.launchfleet.sdk.transport.SdkFlagWire;
import com.launchfleet.sdk.transport.SdkVariantWire;

/**
 * fromWire is the "reject invalid configuration before installing it as the active
 * snapshot" gate - these tests are what let ConfigurationCache trust that any
 * ConfigurationSnapshot it ever installs is structurally sound.
 */
class ConfigurationSnapshotTest {

	private static final SdkVariantWire OFF = new SdkVariantWire("v-off", "false", "Off", Boolean.FALSE);

	private static final SdkVariantWire ON = new SdkVariantWire("v-on", "true", "On", Boolean.TRUE);

	@Test
	void aWellFormedPayloadBuildsSuccessfully() {
		SdkConfigurationWire wire = new SdkConfigurationWire("env-1", "production", "v1", Instant.now().toString(),
				List.of(new SdkFlagWire("checkout", true, OFF, List.of(OFF, ON), List.of(), null, 1)), List.of());

		ConfigurationSnapshot snapshot = ConfigurationSnapshot.fromWire(wire, Instant.now());

		assertThat(snapshot.environmentId()).isEqualTo("env-1");
		assertThat(snapshot.version()).isEqualTo("v1");
		assertThat(snapshot.flag("checkout")).isPresent();
		assertThat(snapshot.flag("missing")).isEmpty();
	}

	@Test
	void missingVersionIsRejected() {
		SdkConfigurationWire wire = new SdkConfigurationWire("env-1", "production", null, null, List.of(), List.of());

		assertThatThrownBy(() -> ConfigurationSnapshot.fromWire(wire, Instant.now()))
				.isInstanceOf(InvalidConfigurationException.class);
	}

	@Test
	void missingEnvironmentIdIsRejected() {
		SdkConfigurationWire wire = new SdkConfigurationWire(null, "production", "v1", null, List.of(), List.of());

		assertThatThrownBy(() -> ConfigurationSnapshot.fromWire(wire, Instant.now()))
				.isInstanceOf(InvalidConfigurationException.class);
	}

	@Test
	void aFlagWithNoVariantsIsRejected() {
		SdkConfigurationWire wire = new SdkConfigurationWire("env-1", "production", "v1", null,
				List.of(new SdkFlagWire("checkout", true, OFF, List.of(), List.of(), null, 1)), List.of());

		assertThatThrownBy(() -> ConfigurationSnapshot.fromWire(wire, Instant.now()))
				.isInstanceOf(InvalidConfigurationException.class);
	}

	@Test
	void aFlagWhoseDefaultVariantIsNotAmongItsOwnVariantsIsRejected() {
		SdkVariantWire strayDefault = new SdkVariantWire("not-a-real-variant", "false", "Off", Boolean.FALSE);
		SdkConfigurationWire wire = new SdkConfigurationWire("env-1", "production", "v1", null,
				List.of(new SdkFlagWire("checkout", true, strayDefault, List.of(OFF, ON), List.of(), null, 1)),
				List.of());

		assertThatThrownBy(() -> ConfigurationSnapshot.fromWire(wire, Instant.now()))
				.isInstanceOf(InvalidConfigurationException.class);
	}

	@Test
	void anEmptyConfigurationIsValid() {
		SdkConfigurationWire wire = new SdkConfigurationWire("env-1", "production", "v1", null, List.of(), List.of());

		ConfigurationSnapshot snapshot = ConfigurationSnapshot.fromWire(wire, Instant.now());

		assertThat(snapshot.flag("anything")).isEmpty();
	}
}

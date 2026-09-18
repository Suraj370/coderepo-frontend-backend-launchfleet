package com.launchfleet.sdk.configuration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.launchfleet.sdk.transport.SdkAllocationWire;
import com.launchfleet.sdk.transport.SdkConditionWire;
import com.launchfleet.sdk.transport.SdkConfigurationWire;
import com.launchfleet.sdk.transport.SdkFlagWire;
import com.launchfleet.sdk.transport.SdkRolloutWire;
import com.launchfleet.sdk.transport.SdkSegmentWire;
import com.launchfleet.sdk.transport.SdkTargetingRuleWire;
import com.launchfleet.sdk.transport.SdkVariantWire;

/**
 * One complete, immutable, evaluable environment configuration - the only shape
 * FlagEvaluator ever reads from. Built once per successful fetch (fromWire) and never
 * mutated afterward: ConfigurationCache atomically swaps a whole new instance in rather
 * than editing an existing one in place, so no evaluator thread can ever observe a
 * half-updated snapshot (see ConfigurationCache).
 *
 * fromWire is also the validation gate "reject invalid configuration before installing
 * it as the active snapshot" - it throws InvalidConfigurationException on structurally
 * unusable input (missing version/environment, a flag with no variants, a default
 * variant id that isn't one of the flag's variants). It deliberately does NOT reject a
 * flag whose targeting rule or rollout allocation references a missing variant id -
 * that's tolerated at evaluation time instead (see SnapshotFlag.variantById /
 * FlagEvaluator), so one bad rule doesn't take down an entire environment's
 * configuration.
 */
public final class ConfigurationSnapshot {

	private final String environmentId;

	private final String environmentKey;

	private final String version;

	private final Instant fetchedAt;

	private final Map<String, SnapshotFlag> flagsByKey;

	private final Map<String, SnapshotSegment> segmentsById;

	private ConfigurationSnapshot(String environmentId, String environmentKey, String version, Instant fetchedAt,
			Map<String, SnapshotFlag> flagsByKey, Map<String, SnapshotSegment> segmentsById) {
		this.environmentId = environmentId;
		this.environmentKey = environmentKey;
		this.version = version;
		this.fetchedAt = fetchedAt;
		this.flagsByKey = Map.copyOf(flagsByKey);
		this.segmentsById = Map.copyOf(segmentsById);
	}

	public static ConfigurationSnapshot fromWire(SdkConfigurationWire wire, Instant fetchedAt) {
		requireNonBlank(wire.environmentId(), "environmentId");
		requireNonBlank(wire.environmentKey(), "environmentKey");
		requireNonBlank(wire.version(), "version");

		Map<String, SnapshotSegment> segmentsById = new LinkedHashMap<>();
		if (wire.segments() != null) {
			for (SdkSegmentWire segment : wire.segments()) {
				requireNonBlank(segment.id(), "segments[].id");
				segmentsById.put(segment.id(), toSegment(segment));
			}
		}

		Map<String, SnapshotFlag> flagsByKey = new LinkedHashMap<>();
		if (wire.flags() != null) {
			for (SdkFlagWire flag : wire.flags()) {
				requireNonBlank(flag.key(), "flags[].key");
				flagsByKey.put(flag.key(), toFlag(flag));
			}
		}

		return new ConfigurationSnapshot(wire.environmentId(), wire.environmentKey(), wire.version(), fetchedAt,
				flagsByKey, segmentsById);
	}

	private static SnapshotFlag toFlag(SdkFlagWire wire) {
		if (wire.variants() == null || wire.variants().isEmpty()) {
			throw new InvalidConfigurationException("Flag '" + wire.key() + "' has no variants.");
		}
		if (wire.defaultVariant() == null) {
			throw new InvalidConfigurationException("Flag '" + wire.key() + "' has no defaultVariant.");
		}

		List<SnapshotVariant> variants = wire.variants().stream().map(ConfigurationSnapshot::toVariant).toList();
		SnapshotVariant defaultVariant = toVariant(wire.defaultVariant());
		boolean defaultVariantKnown = variants.stream().anyMatch(variant -> variant.id().equals(defaultVariant.id()));
		if (!defaultVariantKnown) {
			throw new InvalidConfigurationException(
					"Flag '" + wire.key() + "' defaultVariant is not one of its own variants.");
		}

		List<SnapshotTargetingRule> rules = new ArrayList<>();
		if (wire.targetingRules() != null) {
			for (SdkTargetingRuleWire rule : wire.targetingRules()) {
				rules.add(toRule(rule));
			}
		}
		rules.sort(Comparator.comparingInt(SnapshotTargetingRule::priority).thenComparing(SnapshotTargetingRule::id));

		SnapshotRollout rollout = wire.rollout() == null ? null : toRollout(wire.rollout());

		return new SnapshotFlag(wire.key(), wire.enabled(), defaultVariant, variants, List.copyOf(rules), rollout);
	}

	private static SnapshotVariant toVariant(SdkVariantWire wire) {
		requireNonBlank(wire.id(), "variant id");

		return new SnapshotVariant(wire.id(), wire.key(), wire.name(), wire.value());
	}

	private static SnapshotTargetingRule toRule(SdkTargetingRuleWire wire) {
		requireNonBlank(wire.id(), "targeting rule id");
		requireNonBlank(wire.variantId(), "targeting rule variantId");

		List<SnapshotCondition> conditions = wire.conditions() == null ? List.of()
				: wire.conditions().stream().map(ConfigurationSnapshot::toCondition).toList();

		return new SnapshotTargetingRule(wire.id(), wire.priority(), conditions, wire.variantId());
	}

	private static SnapshotCondition toCondition(SdkConditionWire wire) {
		ConditionType type;
		ConditionOperator operator;
		try {
			type = ConditionType.valueOf(wire.type());
			operator = ConditionOperator.valueOf(wire.operator());
		} catch (IllegalArgumentException | NullPointerException exception) {
			throw new InvalidConfigurationException(
					"Unrecognized condition type/operator: " + wire.type() + "/" + wire.operator());
		}

		List<String> values = wire.values() == null ? List.of() : List.copyOf(wire.values());

		return new SnapshotCondition(type, wire.attribute(), operator, values);
	}

	private static SnapshotRollout toRollout(SdkRolloutWire wire) {
		if (wire.allocations() == null || wire.allocations().isEmpty()) {
			throw new InvalidConfigurationException("A rollout with no allocations was received.");
		}

		return SnapshotRollout.of(wire.allocations().stream().map(ConfigurationSnapshot::toAllocation).toList());
	}

	private static SnapshotAllocation toAllocation(SdkAllocationWire wire) {
		requireNonBlank(wire.variantId(), "allocation variantId");

		return new SnapshotAllocation(wire.variantId(), wire.percentage());
	}

	private static SnapshotSegment toSegment(SdkSegmentWire wire) {
		List<SnapshotCondition> conditions = wire.conditions() == null ? List.of()
				: wire.conditions().stream().map(ConfigurationSnapshot::toCondition).toList();

		return new SnapshotSegment(wire.id(), wire.key(), conditions);
	}

	private static void requireNonBlank(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new InvalidConfigurationException("Configuration is missing required field: " + fieldName);
		}
	}

	public String environmentId() {
		return environmentId;
	}

	public String environmentKey() {
		return environmentKey;
	}

	public String version() {
		return version;
	}

	public Instant fetchedAt() {
		return fetchedAt;
	}

	public Duration age(Instant now) {
		return Duration.between(fetchedAt, now);
	}

	public Optional<SnapshotFlag> flag(String key) {
		return Optional.ofNullable(flagsByKey.get(key));
	}

	public Optional<SnapshotSegment> segment(String id) {
		return Optional.ofNullable(segmentsById.get(id));
	}
}

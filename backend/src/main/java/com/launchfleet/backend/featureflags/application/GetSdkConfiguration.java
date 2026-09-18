package com.launchfleet.backend.featureflags.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.ConditionType;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.featureflags.domain.Variant;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.featureflags.ports.SegmentStore;

/**
 * Assembles the read-only configuration snapshot an SDK credential is allowed to
 * fetch for its own project/environment - the whole of Phase 5's server-side
 * contribution beyond authentication (already handled by SdkCredentialAuthenticationFilter/
 * SecurityConfig.sdkFilterChain, unchanged here).
 *
 * Deliberately narrow, matching every other "Get*"/"List*" use case in this package: it
 * loads FeatureFlag + FeatureFlagConfig + Segment through their existing ports, joins
 * them in memory, and computes an aggregate version - no new port methods, no new
 * persistence, no generic "SdkService". FeatureFlagLookup is reused for project/
 * environment resolution exactly as the dashboard-facing use cases already do.
 *
 * The aggregate version is computed at read time (the locked Phase 5 decision), not
 * persisted: a SHA-256 hash over every included flag config's id+version and every
 * included (referenced) segment's id+updatedAt. It changes exactly when the assembled
 * payload's own content would change, and nothing else - FeatureFlagConfig.version's
 * existing per-mutation bump semantics are untouched.
 */
@Component
public class GetSdkConfiguration {

	private final FeatureFlagLookup lookup;

	private final FeatureFlagStore featureFlagStore;

	private final FeatureFlagConfigStore featureFlagConfigStore;

	private final SegmentStore segmentStore;

	GetSdkConfiguration(FeatureFlagLookup lookup, FeatureFlagStore featureFlagStore,
			FeatureFlagConfigStore featureFlagConfigStore, SegmentStore segmentStore) {
		this.lookup = lookup;
		this.featureFlagStore = featureFlagStore;
		this.featureFlagConfigStore = featureFlagConfigStore;
		this.segmentStore = segmentStore;
	}

	public SdkConfigurationResult execute(String projectKey, String environmentKey) {
		ProjectRef project = lookup.resolveProject(projectKey);
		EnvironmentRef environment = lookup.resolveEnvironment(project, environmentKey);

		Map<String, FeatureFlag> flagsByFeatureFlagId = featureFlagStore.findByProjectId(project.id()).stream()
				.collect(Collectors.toMap(FeatureFlag::getId, flag -> flag));

		List<FeatureFlagConfig> configs = featureFlagConfigStore.findByProjectId(project.id()).stream()
				.filter(config -> config.getEnvironmentId().equals(environment.id()))
				.filter(config -> flagsByFeatureFlagId.containsKey(config.getFeatureFlagId()))
				.sorted(Comparator.comparing(FeatureFlagConfig::getFeatureFlagId))
				.toList();

		List<SdkFlagView> flagViews = new ArrayList<>();
		Set<String> referencedSegmentIds = new HashSet<>();

		for (FeatureFlagConfig config : configs) {
			FeatureFlag flag = flagsByFeatureFlagId.get(config.getFeatureFlagId());
			Variant defaultVariant = variantById(flag, config.getDefaultVariantId());

			flagViews.add(new SdkFlagView(flag.getKey(), config.isEnabled(), defaultVariant, flag.getVariants(),
					config.getTargetingRules(), config.getRollout(), config.getVersion()));

			referencedSegmentIds.addAll(segmentIdsReferencedBy(config.getTargetingRules()));
		}

		List<Segment> segments = segmentStore.findByProjectId(project.id()).stream()
				.filter(segment -> referencedSegmentIds.contains(segment.getId()))
				.sorted(Comparator.comparing(Segment::getId))
				.toList();

		String version = aggregateVersion(configs, segments);

		return new SdkConfigurationResult(environment.id(), environment.key(), version, Instant.now(), flagViews,
				segments);
	}

	private Set<String> segmentIdsReferencedBy(List<TargetingRule> rules) {
		Set<String> ids = new HashSet<>();
		for (TargetingRule rule : rules) {
			for (Condition condition : rule.getConditions()) {
				if (condition.type() == ConditionType.SEGMENT_MATCH) {
					ids.addAll(condition.values());
				}
			}
		}

		return ids;
	}

	private Variant variantById(FeatureFlag flag, String variantId) {
		return flag.getVariants().stream().filter(variant -> variant.id().equals(variantId)).findFirst()
				.orElseThrow(() -> new IllegalStateException("Flag '" + flag.getKey() + "' has no variant '"
						+ variantId + "' - data integrity violation."));
	}

	/**
	 * A canonical string over exactly what's included in the payload (flag config
	 * id+version, referenced segment id+updatedAt), hashed with the same SHA-256/hex
	 * primitive SdkCredentialService already uses for credential hashing - no new
	 * hashing dependency. Both lists are pre-sorted by id at the call site so the
	 * canonical string (and therefore the hash) never depends on Mongo/list ordering.
	 */
	private String aggregateVersion(List<FeatureFlagConfig> configs, List<Segment> segments) {
		StringBuilder canonical = new StringBuilder();
		for (FeatureFlagConfig config : configs) {
			canonical.append(config.getId()).append(':').append(config.getVersion()).append(';');
		}
		for (Segment segment : segments) {
			canonical.append(segment.getId()).append(':').append(segment.getUpdatedAt().toEpochMilli()).append(';');
		}

		return sha256Hex(canonical.toString());
	}

	private String sha256Hex(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");

			return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}
}

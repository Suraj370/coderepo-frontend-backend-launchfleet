package com.launchfleet.backend.featureflags.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The flag's logical identity and definition - key, type, variants - with zero
 * dependency on Spring, MongoDB, or HTTP. Carries no per-environment state; that
 * lives in FeatureFlagConfig, a separate aggregate (see its Javadoc for why).
 * Creation, variant, and lifecycle invariants are enforced here, not scattered
 * across an application service - an invalid FeatureFlag cannot exist.
 */
public class FeatureFlag {

	private final String id;

	private final String projectId;

	private final String key;

	private String name;

	private String description;

	private final FlagType type;

	private FlagStatus status;

	private final List<Variant> variants;

	private final String createdBy;

	private final Instant createdAt;

	private String updatedBy;

	private Instant updatedAt;

	private FeatureFlag(String id, String projectId, String key, String name, String description, FlagType type,
			FlagStatus status, List<Variant> variants, String createdBy, Instant createdAt, String updatedBy,
			Instant updatedAt) {
		this.id = id;
		this.projectId = projectId;
		this.key = key;
		this.name = name;
		this.description = description;
		this.type = type;
		this.status = status;
		this.variants = List.copyOf(variants);
		this.createdBy = createdBy;
		this.createdAt = createdAt;
		this.updatedBy = updatedBy;
		this.updatedAt = updatedAt;
	}

	/**
	 * Creation invariants: key/name are required; a BOOLEAN flag always gets exactly
	 * the fixed true/false pair (never caller-supplied variants), a MULTIVARIANT flag
	 * needs at least two variants with unique keys. id is null until persisted - the
	 * storage adapter assigns it (see FeatureFlagStore).
	 */
	public static FeatureFlag create(String projectId, String key, String name, String description, FlagType type,
			List<Variant> requestedVariants, String actingUserId) {
		if (projectId == null || projectId.isBlank()) {
			throw new IllegalArgumentException("projectId is required.");
		}

		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("key is required.");
		}

		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name is required.");
		}

		if (type == null) {
			throw new IllegalArgumentException("type is required.");
		}

		Instant now = Instant.now();

		return new FeatureFlag(null, projectId, key, name, description, type, FlagStatus.ACTIVE,
				buildVariants(type, requestedVariants), actingUserId, now, actingUserId, now);
	}

	/** Rehydrates a flag already known to be valid, exactly as persisted - storage adapters only. */
	public static FeatureFlag reconstitute(String id, String projectId, String key, String name, String description,
			FlagType type, FlagStatus status, List<Variant> variants, String createdBy, Instant createdAt,
			String updatedBy, Instant updatedAt) {
		return new FeatureFlag(id, projectId, key, name, description, type, status, variants, createdBy, createdAt,
				updatedBy, updatedAt);
	}

	public void retire(String actingUserId) {
		if (status == FlagStatus.RETIRED) {
			throw new IllegalStateException("This flag is already retired.");
		}

		this.status = FlagStatus.RETIRED;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	public void updateMetadata(String name, String description, String actingUserId) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("name is required.");
		}

		this.name = name;
		this.description = description;
		this.updatedBy = actingUserId;
		this.updatedAt = Instant.now();
	}

	/** The variant a fresh FeatureFlagConfig should default to - the first/"off" one. */
	public Variant defaultVariant() {
		return variants.get(0);
	}

	private static List<Variant> buildVariants(FlagType type, List<Variant> requestedVariants) {
		if (type == FlagType.BOOLEAN) {
			if (requestedVariants != null && !requestedVariants.isEmpty()) {
				throw new IllegalArgumentException(
						"Boolean flags cannot have custom variants; true/false are created automatically.");
			}

			return booleanVariants();
		}

		if (requestedVariants == null || requestedVariants.size() < 2) {
			throw new IllegalArgumentException("Multivariant flags need at least two variants.");
		}

		Set<String> seenKeys = new HashSet<>();
		List<Variant> variants = new ArrayList<>();
		int order = 0;

		for (Variant requested : requestedVariants) {
			if (!seenKeys.add(requested.key())) {
				throw new IllegalArgumentException("Variant keys must be unique within a flag: " + requested.key());
			}

			variants.add(new Variant(newVariantId(), requested.key(), requested.name(), requested.value(), order++));
		}

		return variants;
	}

	private static List<Variant> booleanVariants() {
		return List.of(new Variant(newVariantId(), "false", "Off", Boolean.FALSE, 0),
				new Variant(newVariantId(), "true", "On", Boolean.TRUE, 1));
	}

	private static String newVariantId() {
		return UUID.randomUUID().toString();
	}

	public String getId() {
		return id;
	}

	public String getProjectId() {
		return projectId;
	}

	public String getKey() {
		return key;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public FlagType getType() {
		return type;
	}

	public FlagStatus getStatus() {
		return status;
	}

	public List<Variant> getVariants() {
		return variants;
	}

	public String getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public String getUpdatedBy() {
		return updatedBy;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}

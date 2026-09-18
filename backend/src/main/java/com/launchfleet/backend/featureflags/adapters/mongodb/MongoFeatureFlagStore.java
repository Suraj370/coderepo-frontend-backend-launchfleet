package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FlagStatus;
import com.launchfleet.backend.featureflags.domain.FlagType;
import com.launchfleet.backend.featureflags.domain.Variant;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;

/**
 * Translates between the pure domain FeatureFlag and its Mongo-mapped
 * FeatureFlagDocument - the only place in the codebase that does. The translation is
 * mechanical enough to keep inline here rather than as a separate top-level mapper
 * class (see the task's guidance against mapper classes for pure field copying).
 */
@Component
class MongoFeatureFlagStore implements FeatureFlagStore {

	private final SpringDataFeatureFlagRepository repository;

	MongoFeatureFlagStore(SpringDataFeatureFlagRepository repository) {
		this.repository = repository;
	}

	@Override
	public List<FeatureFlag> findByProjectId(String projectId) {
		return repository.findByProjectId(projectId).stream().map(this::toDomain).toList();
	}

	@Override
	public Optional<FeatureFlag> findByProjectIdAndKey(String projectId, String key) {
		return repository.findByProjectIdAndKey(projectId, key).map(this::toDomain);
	}

	@Override
	public boolean existsByProjectIdAndKey(String projectId, String key) {
		return repository.existsByProjectIdAndKey(projectId, key);
	}

	@Override
	public Optional<FeatureFlag> findById(String id) {
		return repository.findById(id).map(this::toDomain);
	}

	@Override
	public FeatureFlag save(FeatureFlag flag) {
		return toDomain(repository.save(toDocument(flag)));
	}

	@Override
	public void deleteAll() {
		repository.deleteAll();
	}

	private FeatureFlag toDomain(FeatureFlagDocument document) {
		List<Variant> variants = document.getVariants().stream()
				.map(v -> new Variant(v.getId(), v.getKey(), v.getName(), v.getValue(), v.getOrder())).toList();

		return FeatureFlag.reconstitute(document.getId(), document.getProjectId(), document.getKey(),
				document.getName(), document.getDescription(), FlagType.valueOf(document.getType()),
				FlagStatus.valueOf(document.getStatus()), variants, document.getCreatedBy(), document.getCreatedAt(),
				document.getUpdatedBy(), document.getUpdatedAt());
	}

	private FeatureFlagDocument toDocument(FeatureFlag flag) {
		FeatureFlagDocument document = new FeatureFlagDocument();
		document.setId(flag.getId());
		document.setProjectId(flag.getProjectId());
		document.setKey(flag.getKey());
		document.setName(flag.getName());
		document.setDescription(flag.getDescription());
		document.setType(flag.getType().name());
		document.setStatus(flag.getStatus().name());
		document.setVariants(flag.getVariants().stream()
				.map(v -> new VariantDocument(v.id(), v.key(), v.name(), v.value(), v.order())).toList());
		document.setCreatedBy(flag.getCreatedBy());
		document.setCreatedAt(flag.getCreatedAt());
		document.setUpdatedBy(flag.getUpdatedBy());
		document.setUpdatedAt(flag.getUpdatedAt());

		return document;
	}
}

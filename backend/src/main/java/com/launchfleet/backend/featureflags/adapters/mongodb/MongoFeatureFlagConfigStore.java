package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.ConditionOperator;
import com.launchfleet.backend.featureflags.domain.ConditionType;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;

import static org.springframework.data.mongodb.core.query.Criteria.where;

@Component
class MongoFeatureFlagConfigStore implements FeatureFlagConfigStore {

	private final SpringDataFeatureFlagConfigRepository repository;

	private final MongoTemplate mongoTemplate;

	MongoFeatureFlagConfigStore(SpringDataFeatureFlagConfigRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public List<FeatureFlagConfig> findByFeatureFlagId(String featureFlagId) {
		return repository.findByFeatureFlagId(featureFlagId).stream().map(this::toDomain).toList();
	}

	@Override
	public Optional<FeatureFlagConfig> findByFeatureFlagIdAndEnvironmentId(String featureFlagId,
			String environmentId) {
		return repository.findByFeatureFlagIdAndEnvironmentId(featureFlagId, environmentId).map(this::toDomain);
	}

	@Override
	public List<FeatureFlagConfig> findByProjectId(String projectId) {
		return repository.findByProjectId(projectId).stream().map(this::toDomain).toList();
	}

	@Override
	public FeatureFlagConfig save(FeatureFlagConfig config) {
		return toDomain(repository.save(toDocument(config)));
	}

	/**
	 * A real MongoDB findAndModify, not a Spring Data repository.save() - the query
	 * filter includes the expected version, so this write only takes effect if no
	 * other write has advanced the document past it since it was loaded/validated (see
	 * FeatureFlagConfigStore's Javadoc and the approvals module, Phase 6). An empty
	 * result means the version had already moved; the caller must not retry with the
	 * same desiredState - see the approvals module's stale-configuration handling.
	 */
	@Override
	public Optional<FeatureFlagConfig> applyIfCurrentVersion(FeatureFlagConfig desiredState, int expectedVersion) {
		Query query = Query.query(where("_id").is(desiredState.getId()).and("version").is(expectedVersion));
		Update update = new Update().set("enabled", desiredState.isEnabled())
				.set("defaultVariantId", desiredState.getDefaultVariantId())
				.set("targetingRules", desiredState.getTargetingRules().stream().map(this::toDocument).toList())
				.set("rollout", desiredState.getRollout() == null ? null : toDocument(desiredState.getRollout()))
				.set("version", expectedVersion + 1)
				.set("updatedBy", desiredState.getUpdatedBy())
				.set("updatedAt", desiredState.getUpdatedAt() == null ? Instant.now() : desiredState.getUpdatedAt());

		FeatureFlagConfigDocument updated = mongoTemplate.findAndModify(query, update,
				org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),
				FeatureFlagConfigDocument.class);

		return Optional.ofNullable(updated).map(this::toDomain);
	}

	@Override
	public void deleteAll() {
		repository.deleteAll();
	}

	private FeatureFlagConfig toDomain(FeatureFlagConfigDocument document) {
		List<TargetingRule> targetingRules = document.getTargetingRules() == null ? List.of()
				: document.getTargetingRules().stream().map(this::toDomain).toList();
		Rollout rollout = document.getRollout() == null ? null : toDomain(document.getRollout());

		return FeatureFlagConfig.reconstitute(document.getId(), document.getFeatureFlagId(),
				document.getEnvironmentId(), document.getProjectId(), document.isEnabled(),
				document.getDefaultVariantId(), targetingRules, rollout, document.getVersion(),
				document.getUpdatedBy(), document.getUpdatedAt(), document.getCreatedAt());
	}

	private Rollout toDomain(RolloutDocument document) {
		List<Allocation> allocations = document.getAllocations().stream().map(this::toDomain).toList();
		return new Rollout(allocations);
	}

	private Allocation toDomain(AllocationDocument document) {
		return new Allocation(document.getVariantId(), document.getPercentage());
	}

	private TargetingRule toDomain(TargetingRuleDocument document) {
		List<Condition> conditions = document.getConditions().stream().map(this::toDomain).toList();
		return TargetingRule.reconstitute(document.getId(), document.getPriority(), conditions,
				document.getVariantId());
	}

	private Condition toDomain(ConditionDocument document) {
		return new Condition(ConditionType.valueOf(document.getType()), document.getAttribute(),
				ConditionOperator.valueOf(document.getOperator()), document.getValues());
	}

	private FeatureFlagConfigDocument toDocument(FeatureFlagConfig config) {
		FeatureFlagConfigDocument document = new FeatureFlagConfigDocument();
		document.setId(config.getId());
		document.setFeatureFlagId(config.getFeatureFlagId());
		document.setEnvironmentId(config.getEnvironmentId());
		document.setProjectId(config.getProjectId());
		document.setEnabled(config.isEnabled());
		document.setDefaultVariantId(config.getDefaultVariantId());
		document.setTargetingRules(config.getTargetingRules().stream().map(this::toDocument).toList());
		document.setRollout(config.getRollout() == null ? null : toDocument(config.getRollout()));
		document.setVersion(config.getVersion());
		document.setUpdatedBy(config.getUpdatedBy());
		document.setUpdatedAt(config.getUpdatedAt());
		document.setCreatedAt(config.getCreatedAt());

		return document;
	}

	private TargetingRuleDocument toDocument(TargetingRule rule) {
		List<ConditionDocument> conditions = rule.getConditions().stream().map(this::toDocument).toList();
		return new TargetingRuleDocument(rule.getId(), rule.getPriority(), conditions, rule.getVariantId());
	}

	private ConditionDocument toDocument(Condition condition) {
		return new ConditionDocument(condition.type().name(), condition.attribute(), condition.operator().name(),
				condition.values());
	}

	private RolloutDocument toDocument(Rollout rollout) {
		return new RolloutDocument(rollout.allocations().stream().map(this::toDocument).toList());
	}

	private AllocationDocument toDocument(Allocation allocation) {
		return new AllocationDocument(allocation.variantId(), allocation.percentage());
	}
}

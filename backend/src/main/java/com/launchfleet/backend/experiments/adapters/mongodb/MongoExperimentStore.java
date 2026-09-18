package com.launchfleet.backend.experiments.adapters.mongodb;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.domain.ExperimentStatus;
import com.launchfleet.backend.experiments.ports.ExperimentConflictException;
import com.launchfleet.backend.experiments.ports.ExperimentStore;
import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.shared.ApiException;

import jakarta.annotation.PostConstruct;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Translates between the pure domain Experiment and its Mongo-mapped
 * ExperimentDocument - mirrors MongoApprovalRequestStore's shape. Updates to an
 * EXISTING experiment go through a single-document, non-transactional MongoDB
 * findAndModify conditioned on {@code _id} AND {@code version} (locked decision 16:
 * optimistic version checks, no MongoDB transactions for ordinary Phase 7
 * operations - findAndModify is one atomic single-document operation, not a
 * multi-document transaction, so this stays inside that boundary). A brand-new
 * experiment (no id yet) is a plain insert, guarded by the (projectId, key) unique
 * index for duplicate-key detection - created explicitly at startup below, since
 * this Spring Data MongoDB setup does not auto-create indexes from @CompoundIndex
 * annotations alone (see MongoExperimentAssignmentStore's identical note).
 */
@Component
class MongoExperimentStore implements ExperimentStore {

	private static final int CONFLICT = 409;

	private final SpringDataExperimentRepository repository;

	private final MongoTemplate mongoTemplate;

	MongoExperimentStore(SpringDataExperimentRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	@PostConstruct
	void ensureUniqueExperimentKeyIndex() {
		Index index = new Index().on("projectId", org.springframework.data.domain.Sort.Direction.ASC)
				.on("key", org.springframework.data.domain.Sort.Direction.ASC).unique().named("project_experiment_key");

		mongoTemplate.indexOps(ExperimentDocument.class).createIndex(index);
	}

	@Override
	public Optional<Experiment> findById(String id) {
		return repository.findById(id).map(this::toDomain);
	}

	@Override
	public Optional<Experiment> findByProjectIdAndKey(String projectId, String key) {
		return repository.findByProjectIdAndKey(projectId, key).map(this::toDomain);
	}

	@Override
	public List<Experiment> findByProjectId(String projectId) {
		return repository.findByProjectId(projectId).stream().map(this::toDomain).toList();
	}

	@Override
	public List<Experiment> findActiveByFeatureFlagId(String featureFlagId) {
		return repository
				.findByFeatureFlagIdAndStatusIn(featureFlagId,
						List.of(ExperimentStatus.DRAFT.name(), ExperimentStatus.RUNNING.name()))
				.stream().map(this::toDomain).toList();
	}

	@Override
	public List<Experiment> findActiveByEnvironmentId(String environmentId) {
		return repository
				.findByEnvironmentIdAndStatusIn(environmentId,
						List.of(ExperimentStatus.DRAFT.name(), ExperimentStatus.RUNNING.name()))
				.stream().map(this::toDomain).toList();
	}

	@Override
	public Experiment save(Experiment experiment) {
		if (experiment.getId() == null) {
			return insert(experiment);
		}

		return updateWithVersionCheck(experiment);
	}

	private Experiment insert(Experiment experiment) {
		try {
			return toDomain(repository.save(toDocument(experiment)));
		} catch (DuplicateKeyException exception) {
			throw new ApiException(CONFLICT, "EXPERIMENT_KEY_TAKEN",
					"An experiment with this key already exists in this project.");
		}
	}

	private Experiment updateWithVersionCheck(Experiment experiment) {
		Query query = Query.query(where("_id").is(experiment.getId()).and("version").is(experiment.getVersion() - 1));
		Update update = new Update().set("name", experiment.getName()).set("description", experiment.getDescription())
				.set("allocation",
						experiment.getAllocation() == null ? null : toAllocationDocuments(experiment.getAllocation()))
				.set("conversionEventName", experiment.getConversionEventName())
				.set("status", experiment.getStatus().name()).set("version", experiment.getVersion())
				.set("updatedBy", experiment.getUpdatedBy()).set("updatedAt", experiment.getUpdatedAt());

		ExperimentDocument updated = mongoTemplate.findAndModify(query, update,
				FindAndModifyOptions.options().returnNew(true), ExperimentDocument.class);

		if (updated == null) {
			throw new ExperimentConflictException(
					"This experiment was changed by another operation before this one could be saved.");
		}

		return toDomain(updated);
	}

	@Override
	public void deleteAll() {
		repository.deleteAll();
	}

	private Experiment toDomain(ExperimentDocument document) {
		Rollout allocation = document.getAllocation() == null ? null : new Rollout(document.getAllocation().stream()
				.map(entry -> new Allocation(entry.getVariantId(), entry.getPercentage())).toList());

		return Experiment.reconstitute(document.getId(), document.getProjectId(), document.getEnvironmentId(),
				document.getFeatureFlagId(), document.getKey(), document.getName(), document.getDescription(),
				allocation, document.getConversionEventName(), ExperimentStatus.valueOf(document.getStatus()),
				document.getVersion(), document.getCreatedBy(), document.getCreatedAt(), document.getUpdatedBy(),
				document.getUpdatedAt());
	}

	private ExperimentDocument toDocument(Experiment experiment) {
		ExperimentDocument document = new ExperimentDocument();
		document.setId(experiment.getId());
		document.setProjectId(experiment.getProjectId());
		document.setEnvironmentId(experiment.getEnvironmentId());
		document.setFeatureFlagId(experiment.getFeatureFlagId());
		document.setKey(experiment.getKey());
		document.setName(experiment.getName());
		document.setDescription(experiment.getDescription());
		document.setAllocation(
				experiment.getAllocation() == null ? null : toAllocationDocuments(experiment.getAllocation()));
		document.setConversionEventName(experiment.getConversionEventName());
		document.setStatus(experiment.getStatus().name());
		document.setVersion(experiment.getVersion());
		document.setCreatedBy(experiment.getCreatedBy());
		document.setCreatedAt(experiment.getCreatedAt());
		document.setUpdatedBy(experiment.getUpdatedBy());
		document.setUpdatedAt(experiment.getUpdatedAt() == null ? Instant.now() : experiment.getUpdatedAt());

		return document;
	}

	private List<ExperimentAllocationEntryDocument> toAllocationDocuments(Rollout allocation) {
		return allocation.allocations().stream()
				.map(entry -> new ExperimentAllocationEntryDocument(entry.variantId(), entry.percentage())).toList();
	}
}

package com.launchfleet.backend.experiments.adapters.mongodb;

import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import com.launchfleet.backend.experiments.domain.ExperimentAssignment;
import com.launchfleet.backend.experiments.ports.ExperimentAssignmentStore;

import jakarta.annotation.PostConstruct;

/**
 * getOrCreate is the whole point of this class: attempts a plain insert, and on a
 * DuplicateKeyException (the unique (experimentId, userKey) index rejecting a
 * second assignment) re-fetches and returns the assignment that actually won,
 * rather than erroring. This is the entire "first write wins, no transactions"
 * mechanism (locked decision 16) - a single unique index plus a catch block, no
 * findAndModify, no session.
 *
 * The index itself is created explicitly at startup, exactly like
 * MongoApprovalRequestStore's ensurePendingUniquenessIndex - this Spring Data
 * MongoDB setup does not auto-create indexes from @CompoundIndex/@Indexed
 * annotations alone (see CreateFeatureFlag's own existsByProjectIdAndKey
 * application-level pre-check, needed for exactly this reason). Without this
 * explicit step, ExperimentAssignmentDocument's @CompoundIndex annotation would be
 * silently decorative and getOrCreate's DuplicateKeyException would never fire.
 */
@Component
class MongoExperimentAssignmentStore implements ExperimentAssignmentStore {

	private final SpringDataExperimentAssignmentRepository repository;

	private final MongoTemplate mongoTemplate;

	MongoExperimentAssignmentStore(SpringDataExperimentAssignmentRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	@PostConstruct
	void ensureUniqueAssignmentIndex() {
		Index index = new Index().on("experimentId", org.springframework.data.domain.Sort.Direction.ASC)
				.on("userKey", org.springframework.data.domain.Sort.Direction.ASC).unique().named("experiment_user");

		mongoTemplate.indexOps(ExperimentAssignmentDocument.class).createIndex(index);
	}

	@Override
	public Optional<ExperimentAssignment> findByExperimentIdAndUserKey(String experimentId, String userKey) {
		return repository.findByExperimentIdAndUserKey(experimentId, userKey).map(this::toDomain);
	}

	@Override
	public ExperimentAssignment getOrCreate(ExperimentAssignment candidate) {
		try {
			return toDomain(repository.save(toDocument(candidate)));
		} catch (DuplicateKeyException exception) {
			return findByExperimentIdAndUserKey(candidate.getExperimentId(), candidate.getUserKey())
					.orElseThrow(() -> new IllegalStateException(
							"Assignment insert conflicted but no existing assignment was found for experiment "
									+ candidate.getExperimentId() + " - this should be unreachable.", exception));
		}
	}

	@Override
	public long countByExperimentId(String experimentId) {
		return repository.countByExperimentId(experimentId);
	}

	@Override
	public long countByExperimentIdAndVariantId(String experimentId, String variantId) {
		return repository.countByExperimentIdAndVariantId(experimentId, variantId);
	}

	@Override
	public void deleteAll() {
		repository.deleteAll();
	}

	private ExperimentAssignment toDomain(ExperimentAssignmentDocument document) {
		return ExperimentAssignment.reconstitute(document.getId(), document.getExperimentId(), document.getUserKey(),
				document.getVariantId(), document.getAssignedAt());
	}

	private ExperimentAssignmentDocument toDocument(ExperimentAssignment assignment) {
		ExperimentAssignmentDocument document = new ExperimentAssignmentDocument();
		document.setId(assignment.getId());
		document.setExperimentId(assignment.getExperimentId());
		document.setUserKey(assignment.getUserKey());
		document.setVariantId(assignment.getVariantId());
		document.setAssignedAt(assignment.getAssignedAt());

		return document;
	}
}

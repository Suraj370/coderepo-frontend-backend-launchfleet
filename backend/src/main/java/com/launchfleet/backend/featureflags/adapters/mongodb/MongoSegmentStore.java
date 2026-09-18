package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.ConditionOperator;
import com.launchfleet.backend.featureflags.domain.ConditionType;
import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.domain.SegmentStatus;
import com.launchfleet.backend.featureflags.ports.SegmentStore;

/**
 * Translates between the pure domain Segment and its Mongo-mapped SegmentDocument -
 * the only place in the codebase that does. The translation is mechanical enough to
 * keep inline here rather than as a separate top-level mapper class (see the task's
 * guidance against mapper classes for pure field copying).
 */
@Component
class MongoSegmentStore implements SegmentStore {

	private final SpringDataSegmentRepository repository;

	MongoSegmentStore(SpringDataSegmentRepository repository) {
		this.repository = repository;
	}

	@Override
	public List<Segment> findByProjectId(String projectId) {
		return repository.findByProjectId(projectId).stream().map(this::toDomain).toList();
	}

	@Override
	public Optional<Segment> findByProjectIdAndKey(String projectId, String key) {
		return repository.findByProjectIdAndKey(projectId, key).map(this::toDomain);
	}

	@Override
	public Optional<Segment> findById(String id) {
		return repository.findById(id).map(this::toDomain);
	}

	@Override
	public boolean existsByProjectIdAndKey(String projectId, String key) {
		return repository.existsByProjectIdAndKey(projectId, key);
	}

	@Override
	public Segment save(Segment segment) {
		return toDomain(repository.save(toDocument(segment)));
	}

	@Override
	public void deleteAll() {
		repository.deleteAll();
	}

	private Segment toDomain(SegmentDocument document) {
		List<Condition> conditions = document.getConditions().stream().map(this::toDomain).toList();

		return Segment.reconstitute(document.getId(), document.getProjectId(), document.getKey(),
				document.getName(), SegmentStatus.valueOf(document.getStatus()), conditions, document.getCreatedBy(),
				document.getCreatedAt(), document.getUpdatedBy(), document.getUpdatedAt());
	}

	private Condition toDomain(ConditionDocument document) {
		return new Condition(ConditionType.valueOf(document.getType()), document.getAttribute(),
				ConditionOperator.valueOf(document.getOperator()), document.getValues());
	}

	private SegmentDocument toDocument(Segment segment) {
		SegmentDocument document = new SegmentDocument();
		document.setId(segment.getId());
		document.setProjectId(segment.getProjectId());
		document.setKey(segment.getKey());
		document.setName(segment.getName());
		document.setStatus(segment.getStatus().name());
		document.setConditions(segment.getConditions().stream().map(this::toDocument).toList());
		document.setCreatedBy(segment.getCreatedBy());
		document.setCreatedAt(segment.getCreatedAt());
		document.setUpdatedBy(segment.getUpdatedBy());
		document.setUpdatedAt(segment.getUpdatedAt());

		return document;
	}

	private ConditionDocument toDocument(Condition condition) {
		return new ConditionDocument(condition.type().name(), condition.attribute(), condition.operator().name(),
				condition.values());
	}
}

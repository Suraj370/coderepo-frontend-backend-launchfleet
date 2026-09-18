package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.time.Instant;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.featureflags.domain.FlagEvaluationEvent;
import com.launchfleet.backend.featureflags.ports.FlagEvaluationEventStore;

@Component
class MongoFlagEvaluationEventStore implements FlagEvaluationEventStore {

	private final SpringDataFlagEvaluationEventRepository repository;

	MongoFlagEvaluationEventStore(SpringDataFlagEvaluationEventRepository repository) {
		this.repository = repository;
	}

	@Override
	public FlagEvaluationEvent save(FlagEvaluationEvent event) {
		return toDomain(repository.save(toDocument(event)));
	}

	@Override
	public long countByProjectIdAndTimestampBetween(String projectId, Instant from, Instant to) {
		return repository.countByProjectIdAndTimestampBetween(projectId, from, to);
	}

	@Override
	public void deleteAll() {
		repository.deleteAll();
	}

	private FlagEvaluationEvent toDomain(FlagEvaluationEventDocument document) {
		return FlagEvaluationEvent.reconstitute(document.getId(), document.getProjectId(),
				document.getEnvironmentId(), document.getFlagId(), document.getUserKey(), document.getVariantId(),
				document.getTimestamp());
	}

	private FlagEvaluationEventDocument toDocument(FlagEvaluationEvent event) {
		FlagEvaluationEventDocument document = new FlagEvaluationEventDocument();
		document.setId(event.getId());
		document.setProjectId(event.getProjectId());
		document.setEnvironmentId(event.getEnvironmentId());
		document.setFlagId(event.getFlagId());
		document.setUserKey(event.getUserKey());
		document.setVariantId(event.getVariantId());
		document.setTimestamp(event.getTimestamp());

		return document;
	}
}

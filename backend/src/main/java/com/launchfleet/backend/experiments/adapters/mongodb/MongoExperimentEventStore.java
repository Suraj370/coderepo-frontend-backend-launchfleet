package com.launchfleet.backend.experiments.adapters.mongodb;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.launchfleet.backend.experiments.domain.ExperimentEvent;
import com.launchfleet.backend.experiments.ports.ExperimentEventStore;

import static org.springframework.data.mongodb.core.query.Criteria.where;

/**
 * Query-time aggregation only (locked decision 10) - no rollup collection, no
 * background job. countDistinctUsersByExperimentIdAndVariantIdAndEventName runs a
 * single MongoTemplate distinct-value query at read time, backed by the
 * experiment_variant_event compound index (see ExperimentEventDocument).
 */
@Component
class MongoExperimentEventStore implements ExperimentEventStore {

	private final SpringDataExperimentEventRepository repository;

	private final MongoTemplate mongoTemplate;

	MongoExperimentEventStore(SpringDataExperimentEventRepository repository, MongoTemplate mongoTemplate) {
		this.repository = repository;
		this.mongoTemplate = mongoTemplate;
	}

	@Override
	public ExperimentEvent save(ExperimentEvent event) {
		return toDomain(repository.save(toDocument(event)));
	}

	@Override
	public long countDistinctUsersByExperimentIdAndVariantIdAndEventName(String experimentId, String variantId,
			String eventName) {
		Query query = Query.query(where("experimentId").is(experimentId).and("variantId").is(variantId)
				.and("eventName").is(eventName));

		return mongoTemplate.findDistinct(query, "userKey", ExperimentEventDocument.class, String.class).size();
	}

	@Override
	public void deleteAll() {
		repository.deleteAll();
	}

	private ExperimentEvent toDomain(ExperimentEventDocument document) {
		return ExperimentEvent.reconstitute(document.getId(), document.getProjectId(), document.getEnvironmentId(),
				document.getExperimentId(), document.getUserKey(), document.getEventName(), document.getVariantId(),
				document.getTimestamp());
	}

	private ExperimentEventDocument toDocument(ExperimentEvent event) {
		ExperimentEventDocument document = new ExperimentEventDocument();
		document.setId(event.getId());
		document.setProjectId(event.getProjectId());
		document.setEnvironmentId(event.getEnvironmentId());
		document.setExperimentId(event.getExperimentId());
		document.setUserKey(event.getUserKey());
		document.setEventName(event.getEventName());
		document.setVariantId(event.getVariantId());
		document.setTimestamp(event.getTimestamp());

		return document;
	}
}

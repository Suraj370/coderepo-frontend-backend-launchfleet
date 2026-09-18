package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.time.Instant;

import org.springframework.data.mongodb.repository.MongoRepository;

interface SpringDataFlagEvaluationEventRepository extends MongoRepository<FlagEvaluationEventDocument, String> {

	long countByProjectIdAndTimestampBetween(String projectId, Instant from, Instant to);

}

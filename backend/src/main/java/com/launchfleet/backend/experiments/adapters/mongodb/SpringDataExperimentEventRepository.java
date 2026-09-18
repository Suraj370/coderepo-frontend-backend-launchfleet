package com.launchfleet.backend.experiments.adapters.mongodb;

import org.springframework.data.mongodb.repository.MongoRepository;

/** Implementation detail of MongoExperimentEventStore only - never exposed outside this package. */
interface SpringDataExperimentEventRepository extends MongoRepository<ExperimentEventDocument, String> {
}

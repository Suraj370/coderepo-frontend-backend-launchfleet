package com.launchfleet.backend.featureflags.adapters.mongodb;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

/**
 * Implementation detail of MongoSegmentStore only - never exposed outside this
 * package. The application layer depends on the SegmentStore port, not on this.
 */
interface SpringDataSegmentRepository extends MongoRepository<SegmentDocument, String> {

	List<SegmentDocument> findByProjectId(String projectId);

	Optional<SegmentDocument> findByProjectIdAndKey(String projectId, String key);

	boolean existsByProjectIdAndKey(String projectId, String key);

}

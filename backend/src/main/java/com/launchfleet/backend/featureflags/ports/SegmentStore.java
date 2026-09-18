package com.launchfleet.backend.featureflags.ports;

import java.util.List;
import java.util.Optional;

import com.launchfleet.backend.featureflags.domain.Segment;

/**
 * The persistence boundary for Segment - implemented by an infrastructure adapter
 * (see adapters.mongodb.MongoSegmentStore). The application layer depends only on
 * this interface, never on MongoDB types.
 */
public interface SegmentStore {

	List<Segment> findByProjectId(String projectId);

	Optional<Segment> findByProjectIdAndKey(String projectId, String key);

	Optional<Segment> findById(String id);

	boolean existsByProjectIdAndKey(String projectId, String key);

	Segment save(Segment segment);

	void deleteAll();

}

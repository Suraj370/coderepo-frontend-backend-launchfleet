package com.launchfleet.backend.environments;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface EnvironmentRepository extends MongoRepository<Environment, String> {

	List<Environment> findByProjectId(String projectId);

	Optional<Environment> findByProjectIdAndKey(String projectId, String key);

}

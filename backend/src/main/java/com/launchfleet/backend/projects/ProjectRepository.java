package com.launchfleet.backend.projects;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ProjectRepository extends MongoRepository<Project, String> {

	Optional<Project> findByKey(String key);

}

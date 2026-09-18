package com.launchfleet.backend.activity;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ActivityLogRepository extends MongoRepository<ActivityLog, String> {

	List<ActivityLog> findTop50ByProjectIdOrderByOccurredAtDesc(String projectId);

}

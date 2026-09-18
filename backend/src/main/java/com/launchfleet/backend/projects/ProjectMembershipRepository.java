package com.launchfleet.backend.projects;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.launchfleet.backend.users.Role;

public interface ProjectMembershipRepository extends MongoRepository<ProjectMembership, String> {

	Optional<ProjectMembership> findByUserIdAndProjectKey(String userId, String projectKey);

	Optional<ProjectMembership> findByIdAndProjectKey(String id, String projectKey);

	List<ProjectMembership> findByUserId(String userId);

	List<ProjectMembership> findByProjectKey(String projectKey);

	long countByProjectKeyAndRole(String projectKey, Role role);

}

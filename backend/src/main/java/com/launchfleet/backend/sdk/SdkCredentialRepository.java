package com.launchfleet.backend.sdk;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface SdkCredentialRepository extends MongoRepository<SdkCredential, String> {

	Optional<SdkCredential> findBySecretHashAndActiveTrue(String secretHash);

	Optional<SdkCredential> findByClientSideIdAndActiveTrue(String clientSideId);

	List<SdkCredential> findByProjectKey(String projectKey);

}

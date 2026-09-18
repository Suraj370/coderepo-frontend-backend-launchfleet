package com.launchfleet.backend.environments.application;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.environments.Environment;
import com.launchfleet.backend.environments.EnvironmentRepository;
import com.launchfleet.backend.environments.EnvironmentStatus;
import com.launchfleet.backend.environments.ports.EnvironmentCreatedListener;
import com.launchfleet.backend.shared.ApiException;

/**
 * Creates an environment and, as one operation, notifies every registered
 * EnvironmentCreatedListener (see the port's Javadoc) - this is what closes the
 * synchronization gap with featureflags/ without environments/ knowing FeatureFlag
 * exists at all. Not made atomic with the listener side effects (see this module's
 * consistency notes in the final report) - MongoDB here is a single, non-transactional
 * persistence layer, and no distributed-transaction machinery is introduced for it.
 */
@Component
public class CreateEnvironment {

	private static final int BAD_REQUEST = 400;

	private static final int CONFLICT = 409;

	private final EnvironmentRepository environmentRepository;

	private final List<EnvironmentCreatedListener> listeners;

	public CreateEnvironment(EnvironmentRepository environmentRepository, List<EnvironmentCreatedListener> listeners) {
		this.environmentRepository = environmentRepository;
		this.listeners = listeners;
	}

	public Environment execute(String projectId, String key, String name, String actingUserId) {
		if (key == null || key.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "key is required.");
		}

		if (name == null || name.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "name is required.");
		}

		if (environmentRepository.findByProjectIdAndKey(projectId, key).isPresent()) {
			throw new ApiException(CONFLICT, "ENVIRONMENT_KEY_TAKEN",
					"An environment with this key already exists in this project.");
		}

		Environment environment = new Environment();
		environment.setProjectId(projectId);
		environment.setKey(key);
		environment.setName(name);
		environment.setStatus(EnvironmentStatus.ACTIVE);
		environment.setCreatedAt(Instant.now());
		Environment saved = environmentRepository.save(environment);

		for (EnvironmentCreatedListener listener : listeners) {
			listener.onEnvironmentCreated(projectId, saved.getId(), saved.getKey(), actingUserId);
		}

		return saved;
	}
}

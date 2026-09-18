package com.launchfleet.backend.environments.application;

import java.util.List;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.environments.Environment;
import com.launchfleet.backend.environments.EnvironmentRepository;
import com.launchfleet.backend.environments.EnvironmentStatus;
import com.launchfleet.backend.environments.ports.EnvironmentRetiredListener;
import com.launchfleet.backend.shared.ApiException;

/**
 * Mirrors featureflags.application.RetireFeatureFlag's shape (same ACTIVE/RETIRED
 * lifecycle, same "already retired" 409, same listener-notification pattern - see
 * EnvironmentRetiredListener's Javadoc for why environments/ notifies rather than
 * reaching into approvals/ directly).
 */
@Component
public class RetireEnvironment {

	private static final int CONFLICT = 409;

	private final EnvironmentRepository environmentRepository;

	private final List<EnvironmentRetiredListener> listeners;

	public RetireEnvironment(EnvironmentRepository environmentRepository, List<EnvironmentRetiredListener> listeners) {
		this.environmentRepository = environmentRepository;
		this.listeners = listeners;
	}

	public Environment execute(Environment environment, String actingUserId) {
		if (environment.getStatus() == EnvironmentStatus.RETIRED) {
			throw new ApiException(CONFLICT, "ALREADY_RETIRED", "This environment is already retired.");
		}

		environment.setStatus(EnvironmentStatus.RETIRED);
		Environment saved = environmentRepository.save(environment);

		for (EnvironmentRetiredListener listener : listeners) {
			listener.onEnvironmentRetired(saved.getProjectId(), saved.getId(), saved.getKey(), actingUserId);
		}

		return saved;
	}
}

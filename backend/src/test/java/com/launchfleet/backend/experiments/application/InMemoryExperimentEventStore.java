package com.launchfleet.backend.experiments.application;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.launchfleet.backend.experiments.domain.ExperimentEvent;
import com.launchfleet.backend.experiments.ports.ExperimentEventStore;

class InMemoryExperimentEventStore implements ExperimentEventStore {

	private final List<ExperimentEvent> events = new CopyOnWriteArrayList<>();

	@Override
	public ExperimentEvent save(ExperimentEvent event) {
		ExperimentEvent toStore = ExperimentEvent.reconstitute(UUID.randomUUID().toString(), event.getProjectId(),
				event.getEnvironmentId(), event.getExperimentId(), event.getUserKey(), event.getEventName(),
				event.getVariantId(), event.getTimestamp());
		events.add(toStore);

		return toStore;
	}

	@Override
	public long countDistinctUsersByExperimentIdAndVariantIdAndEventName(String experimentId, String variantId,
			String eventName) {
		return events.stream()
				.filter(e -> e.getExperimentId().equals(experimentId) && e.getVariantId().equals(variantId)
						&& e.getEventName().equals(eventName))
				.map(ExperimentEvent::getUserKey).distinct().count();
	}

	@Override
	public void deleteAll() {
		events.clear();
	}
}

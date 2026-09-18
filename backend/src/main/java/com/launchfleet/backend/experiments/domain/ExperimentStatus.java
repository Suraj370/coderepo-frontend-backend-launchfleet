package com.launchfleet.backend.experiments.domain;

/**
 * The locked Phase 7 lifecycle: DRAFT -> RUNNING -> COMPLETED, plus CANCELLED for
 * when an experiment becomes invalid because of a lifecycle condition (flag/
 * environment retirement) rather than a deliberate stop. No PAUSED/ARCHIVED - not
 * part of the locked decisions.
 */
public enum ExperimentStatus {

	DRAFT,

	RUNNING,

	COMPLETED,

	CANCELLED

}

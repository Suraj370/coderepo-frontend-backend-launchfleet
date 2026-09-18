package com.launchfleet.backend.approvals;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A single injectable Clock bean so ApplyDueScheduledApprovals's "is this due yet"
 * check can be driven by a fixed/controllable Clock in tests (see locked
 * architecture rule 25: "use controllable time... tests must deterministically prove
 * scheduled execution behavior") instead of ever waiting out real wall-clock delays.
 */
@Configuration
class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}

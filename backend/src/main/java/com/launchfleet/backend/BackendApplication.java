package com.launchfleet.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

// EnableScheduling powers ScheduledApprovalPoller (Phase 6) - the only @Scheduled
// task in the app. Its actual polling logic (ApplyDueScheduledApprovals) is directly,
// synchronously callable, so tests never depend on this annotation actually firing on
// a timer.
@SpringBootApplication
@EnableMongoAuditing
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendApplication.class, args);
	}

}

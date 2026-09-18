package com.launchfleet.backend.approvals;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Multi-document MongoDB transaction support for the one place in this application
 * that genuinely needs cross-aggregate atomicity: applying a SCHEDULED approval
 * request's proposed configuration (see ApplyDueScheduledApprovals). Requires the
 * configured MongoDB to be a replica set (even a single-node one) - a standalone
 * mongod cannot start a transaction at all; see application.yaml's
 * replicaSet=rs0&directConnection=true.
 *
 * No other part of the application uses @Transactional or TransactionTemplate today
 * - registering this bean does not change the behavior of any existing, already-
 * non-transactional write path (FeatureFlagConfig's own optimistic
 * applyIfCurrentVersion, ApprovalRequest's own single-document conditional saves for
 * approve/reject/cancel/manual-schedule, etc. all remain exactly as they were).
 */
@Configuration
class ApprovalTransactionConfig {

	@Bean
	MongoTransactionManager mongoTransactionManager(MongoDatabaseFactory databaseFactory) {
		return new MongoTransactionManager(databaseFactory);
	}

	@Bean
	TransactionTemplate scheduledApprovalTransactionTemplate(MongoTransactionManager mongoTransactionManager) {
		return new TransactionTemplate(mongoTransactionManager);
	}
}

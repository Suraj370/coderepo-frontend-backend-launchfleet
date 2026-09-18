package com.launchfleet.backend.approvals.application;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

/**
 * A transaction manager with no real transactional resource behind it, for the pure
 * in-memory application-layer tests (ApprovalWorkflowUseCasesTest) that use
 * InMemoryApprovalRequestStore/InMemoryFeatureFlagConfigStore instead of real
 * MongoDB - those fakes have no concept of a database transaction to join, so
 * ApplyDueScheduledApprovals' TransactionTemplate just needs something that runs the
 * callback directly. The REAL cross-aggregate atomicity guarantee (a genuine MongoDB
 * multi-document transaction, with real commit/rollback) is exercised by
 * ApprovalRequestLifecycleConcurrencyTest against real (Dockerized) MongoDB instead -
 * this class deliberately does not simulate rollback, since faking that correctly
 * would just be re-implementing MongoTransactionManager's job for no benefit.
 */
final class NoOpTransactionManager extends AbstractPlatformTransactionManager {

	@Override
	protected Object doGetTransaction() {
		return new Object();
	}

	@Override
	protected void doBegin(Object transaction, TransactionDefinition definition) {
	}

	@Override
	protected void doCommit(DefaultTransactionStatus status) {
	}

	@Override
	protected void doRollback(DefaultTransactionStatus status) {
	}
}

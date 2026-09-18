package com.launchfleet.backend.approvals.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Pure unit tests of ApprovalRequest's centralized transition logic (locked
 * architecture rule 26) - no Spring, no MongoDB, no application-layer orchestration.
 */
class ApprovalRequestTest {

	private static final ProposedConfig PROPOSED = new ProposedConfig(true, "v-on", List.of(), null);

	private ApprovalRequest freshPending() {
		return ApprovalRequest.propose("project-1", "flag-1", "env-1", 1, PROPOSED, "alice");
	}

	@Test
	void proposeStartsPendingAndCapturesSubmitterAndBaseVersion() {
		ApprovalRequest request = freshPending();

		assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING);
		assertThat(request.getSubmittedBy()).isEqualTo("alice");
		assertThat(request.getBaseConfigVersion()).isEqualTo(1);
		assertThat(request.getSubmittedAt()).isNotNull();
		assertThat(request.getProposedConfig()).isEqualTo(PROPOSED);
	}

	@Test
	void theSubmitterCanNeverApproveTheirOwnRequestEvenNotionallyAsAnAdmin() {
		ApprovalRequest request = freshPending();

		assertThatThrownBy(() -> request.approve("alice", null)).isInstanceOf(IllegalStateException.class);
		assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING);
	}

	@Test
	void theSubmitterCanNeverRejectTheirOwnRequest() {
		ApprovalRequest request = freshPending();

		assertThatThrownBy(() -> request.reject("alice", "not good")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void aDifferentReviewerCanApprove() {
		ApprovalRequest request = freshPending();

		request.approve("bob", "looks fine");

		assertThat(request.getStatus()).isEqualTo(ApprovalStatus.APPROVED);
		assertThat(request.getReviewedBy()).isEqualTo("bob");
		assertThat(request.getReviewedAt()).isNotNull();
		assertThat(request.getApprovalComment()).isEqualTo("looks fine");
	}

	@Test
	void approvalCommentIsOptional() {
		ApprovalRequest request = freshPending();

		request.approve("bob", null);

		assertThat(request.getApprovalComment()).isNull();
	}

	@Test
	void applyNowRequiresApprovedStatusAndSetsAppliedVersion() {
		ApprovalRequest request = freshPending();
		request.approve("bob", null);

		request.applyNow(11);

		assertThat(request.getStatus()).isEqualTo(ApprovalStatus.APPLIED);
		assertThat(request.getAppliedVersion()).isEqualTo(11);
	}

	@Test
	void applyNowOnAPendingRequestIsRejected() {
		ApprovalRequest request = freshPending();

		assertThatThrownBy(() -> request.applyNow(11)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void scheduleForRequiresAStrictlyFutureTime() {
		ApprovalRequest request = freshPending();
		request.approve("bob", null);
		Instant now = Instant.now();

		assertThatThrownBy(() -> request.scheduleFor(now, now)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> request.scheduleFor(now.minus(1, ChronoUnit.MINUTES), now))
				.isInstanceOf(IllegalArgumentException.class);

		request.scheduleFor(now.plus(1, ChronoUnit.MINUTES), now);
		assertThat(request.getStatus()).isEqualTo(ApprovalStatus.SCHEDULED);
	}

	@Test
	void applyScheduledRequiresScheduledStatus() {
		ApprovalRequest request = freshPending();

		assertThatThrownBy(() -> request.applyScheduled(11)).isInstanceOf(IllegalStateException.class);

		request.approve("bob", null);
		request.scheduleFor(Instant.now().plusSeconds(60), Instant.now());
		request.applyScheduled(11);

		assertThat(request.getStatus()).isEqualTo(ApprovalStatus.APPLIED);
		assertThat(request.getAppliedVersion()).isEqualTo(11);
	}

	@Test
	void failRequiresScheduledStatusAndIsTerminal() {
		ApprovalRequest request = freshPending();
		request.approve("bob", null);
		request.scheduleFor(Instant.now().plusSeconds(60), Instant.now());

		request.fail();

		assertThat(request.getStatus()).isEqualTo(ApprovalStatus.FAILED);
		assertThatThrownBy(() -> request.applyScheduled(1)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void rejectRequiresANonBlankComment() {
		ApprovalRequest request = freshPending();

		assertThatThrownBy(() -> request.reject("bob", null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> request.reject("bob", "   ")).isInstanceOf(IllegalArgumentException.class);

		request.reject("bob", "not aligned with rollout plan");
		assertThat(request.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
		assertThat(request.getRejectionComment()).isEqualTo("not aligned with rollout plan");
	}

	@Test
	void rejectedIsTerminalAndCanNeverBeApprovedAfterward() {
		ApprovalRequest request = freshPending();
		request.reject("bob", "no");

		assertThatThrownBy(() -> request.approve("bob", null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> request.reject("bob", "again")).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void cancelWorksFromPendingOrScheduledButNotFromTerminalStatuses() {
		ApprovalRequest pending = freshPending();
		pending.cancel("alice", CancellationReason.ADMIN_CANCELLED);
		assertThat(pending.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
		assertThat(pending.getCancellationReason()).isEqualTo(CancellationReason.ADMIN_CANCELLED);
		assertThat(pending.getCancelledBy()).isEqualTo("alice");
		assertThat(pending.getCancelledAt()).isNotNull();

		ApprovalRequest scheduled = freshPending();
		scheduled.approve("bob", null);
		scheduled.scheduleFor(Instant.now().plusSeconds(60), Instant.now());
		scheduled.cancel("bob", CancellationReason.ADMIN_CANCELLED);
		assertThat(scheduled.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);

		ApprovalRequest applied = freshPending();
		applied.approve("bob", null);
		applied.applyNow(2);
		assertThatThrownBy(() -> applied.cancel("bob", CancellationReason.ADMIN_CANCELLED))
				.isInstanceOf(IllegalStateException.class);

		ApprovalRequest rejected = freshPending();
		rejected.reject("bob", "no");
		assertThatThrownBy(() -> rejected.cancel("bob", CancellationReason.ADMIN_CANCELLED))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void invalidTransitionsAreImpossible() {
		// REJECTED -> APPROVED / APPLIED
		ApprovalRequest rejected = freshPending();
		rejected.reject("bob", "no");
		assertThatThrownBy(() -> rejected.approve("bob", null)).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> rejected.applyNow(1)).isInstanceOf(IllegalStateException.class);

		// APPLIED -> SCHEDULED / CANCELLED
		ApprovalRequest applied = freshPending();
		applied.approve("bob", null);
		applied.applyNow(2);
		assertThatThrownBy(() -> applied.scheduleFor(Instant.now().plusSeconds(60), Instant.now()))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> applied.cancel("bob", CancellationReason.ADMIN_CANCELLED))
				.isInstanceOf(IllegalStateException.class);

		// FAILED -> APPLIED
		ApprovalRequest failed = freshPending();
		failed.approve("bob", null);
		failed.scheduleFor(Instant.now().plusSeconds(60), Instant.now());
		failed.fail();
		assertThatThrownBy(() -> failed.applyScheduled(3)).isInstanceOf(IllegalStateException.class);

		// CANCELLED -> APPROVED
		ApprovalRequest cancelled = freshPending();
		cancelled.cancel("alice", CancellationReason.ADMIN_CANCELLED);
		assertThatThrownBy(() -> cancelled.approve("bob", null)).isInstanceOf(IllegalStateException.class);

		// SCHEDULED -> APPROVED (approve is only valid from PENDING)
		ApprovalRequest scheduled = freshPending();
		scheduled.approve("bob", null);
		scheduled.scheduleFor(Instant.now().plusSeconds(60), Instant.now());
		assertThatThrownBy(() -> scheduled.approve("carol", null)).isInstanceOf(IllegalStateException.class);
	}

	@Test
	void proposedConfigCannotBeConstructedWithoutARequiredDefaultVariant() {
		assertThatThrownBy(() -> new ProposedConfig(true, null, List.of(), null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new ProposedConfig(true, "  ", List.of(), null))
				.isInstanceOf(IllegalArgumentException.class);
	}
}

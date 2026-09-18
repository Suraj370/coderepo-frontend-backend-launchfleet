package com.launchfleet.backend.approvals.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.launchfleet.backend.approvals.application.SubmitApprovalRequest.AllocationInput;
import com.launchfleet.backend.approvals.application.SubmitApprovalRequest.TargetingRuleInput;
import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.ApprovalStatus;
import com.launchfleet.backend.approvals.domain.CancellationReason;
import com.launchfleet.backend.featureflags.application.TargetingRuleValidator;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.FlagStatus;
import com.launchfleet.backend.featureflags.domain.FlagType;
import com.launchfleet.backend.featureflags.ports.EnvironmentRef;
import com.launchfleet.backend.featureflags.ports.ProjectRef;
import com.launchfleet.backend.shared.ApiException;

/**
 * Application-level coverage for the whole Phase 6 approval workflow, using fake
 * in-memory ports - no Spring, no MongoDB (see FeatureFlagUseCasesTest for the same
 * pattern in featureflags/). RBAC role gating (@PreAuthorize) and the Mongo partial-
 * unique-index backstop are covered separately by ApprovalRequestApiTest, which runs
 * against real HTTP and a real (Dockerized) MongoDB.
 */
class ApprovalWorkflowUseCasesTest {

	private final InMemoryProjectLookup projectLookup = new InMemoryProjectLookup();

	private final InMemoryEnvironmentLookup environmentLookup = new InMemoryEnvironmentLookup();

	private final InMemoryFeatureFlagStore featureFlagStore = new InMemoryFeatureFlagStore();

	private final InMemoryFeatureFlagConfigStore featureFlagConfigStore = new InMemoryFeatureFlagConfigStore();

	private final InMemoryApprovalRequestStore approvalRequestStore = new InMemoryApprovalRequestStore();

	private ApprovalRequestLookup lookup;

	private SubmitApprovalRequest submitApprovalRequest;

	private ApproveApprovalRequest approveApprovalRequest;

	private RejectApprovalRequest rejectApprovalRequest;

	private ScheduleApprovalRequest scheduleApprovalRequest;

	private CancelApprovalRequest cancelApprovalRequest;

	private ApplyDueScheduledApprovals applyDueScheduledApprovals;

	private FlagRetirementCancellationListener flagRetirementListener;

	private EnvironmentRetirementCancellationListener environmentRetirementListener;

	private ProjectRef project;

	private EnvironmentRef environment;

	private FeatureFlag flag;

	private String onVariantId;

	private String offVariantId;

	private MutableClock clock;

	@BeforeEach
	void setUp() {
		lookup = new ApprovalRequestLookup(projectLookup, environmentLookup, featureFlagStore, approvalRequestStore);
		TargetingRuleValidator validator = new TargetingRuleValidator(new com.launchfleet.backend.featureflags.ports.SegmentStore() {
			@Override
			public java.util.Optional<com.launchfleet.backend.featureflags.domain.Segment> findById(String id) {
				return java.util.Optional.empty();
			}

			@Override
			public java.util.Optional<com.launchfleet.backend.featureflags.domain.Segment> findByProjectIdAndKey(
					String projectId, String key) {
				return java.util.Optional.empty();
			}

			@Override
			public boolean existsByProjectIdAndKey(String projectId, String key) {
				return false;
			}

			@Override
			public List<com.launchfleet.backend.featureflags.domain.Segment> findByProjectId(String projectId) {
				return List.of();
			}

			@Override
			public com.launchfleet.backend.featureflags.domain.Segment save(
					com.launchfleet.backend.featureflags.domain.Segment segment) {
				return segment;
			}

			@Override
			public void deleteAll() {
			}
		});

		submitApprovalRequest = new SubmitApprovalRequest(lookup, approvalRequestStore, featureFlagConfigStore,
				validator, new InMemoryActivityRecorder());
		ApplyProposedConfiguration applyProposedConfiguration = new ApplyProposedConfiguration(featureFlagStore,
				featureFlagConfigStore, environmentLookup);
		approveApprovalRequest = new ApproveApprovalRequest(lookup, approvalRequestStore, applyProposedConfiguration,
				new InMemoryActivityRecorder());
		rejectApprovalRequest = new RejectApprovalRequest(lookup, approvalRequestStore, new InMemoryActivityRecorder());
		clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
		scheduleApprovalRequest = new ScheduleApprovalRequest(lookup, approvalRequestStore, applyProposedConfiguration,
				clock);
		cancelApprovalRequest = new CancelApprovalRequest(lookup, approvalRequestStore);
		applyDueScheduledApprovals = new ApplyDueScheduledApprovals(approvalRequestStore, applyProposedConfiguration,
				clock, new org.springframework.transaction.support.TransactionTemplate(new NoOpTransactionManager()));
		flagRetirementListener = new FlagRetirementCancellationListener(approvalRequestStore);
		environmentRetirementListener = new EnvironmentRetirementCancellationListener(approvalRequestStore);

		project = projectLookup.addProject("acme");
		environment = environmentLookup.addEnvironment(project.id(), "production");
		flag = featureFlagStore.save(FeatureFlag.create(project.id(), "checkout", "Checkout", null, FlagType.BOOLEAN,
				null, "creator"));
		offVariantId = flag.getVariants().get(0).id();
		onVariantId = flag.getVariants().get(1).id();
		featureFlagConfigStore
				.save(FeatureFlagConfig.createDisabled(flag.getId(), environment.id(), project.id(), offVariantId, "creator"));
	}

	private ApprovalRequest submit(String submitter) {
		return submitApprovalRequest.execute("acme", "checkout", "production", true, onVariantId, List.of(), List.of(),
				submitter);
	}

	// --- PENDING REQUEST uniqueness ---

	@Test
	void firstProposalSucceeds() {
		ApprovalRequest request = submit("alice");

		assertThat(request.getStatus()).isEqualTo(ApprovalStatus.PENDING);
		assertThat(request.getId()).isNotNull();
	}

	@Test
	void secondPendingProposalForTheSameScopeFails() {
		submit("alice");

		assertThatThrownBy(() -> submit("bob")).isInstanceOf(ApiException.class)
				.satisfies(exception -> assertThat(((ApiException) exception).getStatusCode()).isEqualTo(409));
	}

	@Test
	void differentEnvironmentCanHaveItsOwnPendingRequest() {
		EnvironmentRef staging = environmentLookup.addEnvironment(project.id(), "staging");
		featureFlagConfigStore.save(
				FeatureFlagConfig.createDisabled(flag.getId(), staging.id(), project.id(), offVariantId, "creator"));
		submit("alice");

		ApprovalRequest stagingRequest = submitApprovalRequest.execute("acme", "checkout", "staging", true,
				onVariantId, List.of(), List.of(), "bob");

		assertThat(stagingRequest.getStatus()).isEqualTo(ApprovalStatus.PENDING);
	}

	@Test
	void differentFlagCanHaveItsOwnPendingRequest() {
		FeatureFlag otherFlag = featureFlagStore
				.save(FeatureFlag.create(project.id(), "other-flag", "Other", null, FlagType.BOOLEAN, null, "creator"));
		featureFlagConfigStore.save(FeatureFlagConfig.createDisabled(otherFlag.getId(), environment.id(), project.id(),
				otherFlag.getVariants().get(0).id(), "creator"));
		submit("alice");

		ApprovalRequest otherRequest = submitApprovalRequest.execute("acme", "other-flag", "production", true,
				otherFlag.getVariants().get(1).id(), List.of(), List.of(), "bob");

		assertThat(otherRequest.getStatus()).isEqualTo(ApprovalStatus.PENDING);
	}

	@Test
	void resolvedRequestsDoNotBlockNewProposals() {
		ApprovalRequest first = submit("alice");
		rejectApprovalRequest.execute("acme", first.getId(), "bob", "no thanks");

		ApprovalRequest second = submit("alice");

		assertThat(second.getStatus()).isEqualTo(ApprovalStatus.PENDING);
	}

	// --- PROPOSAL ---

	@Test
	void proposalCapturesCurrentActiveConfigVersion() {
		ApprovalRequest request = submit("alice");

		assertThat(request.getBaseConfigVersion()).isEqualTo(1);
	}

	@Test
	void proposalCapturesTheCompleteProposedSnapshot() {
		ApprovalRequest request = submitApprovalRequest.execute("acme", "checkout", "production", true, onVariantId,
				List.of(new TargetingRuleInput(0,
						List.of(new com.launchfleet.backend.featureflags.domain.Condition(
								com.launchfleet.backend.featureflags.domain.ConditionType.USER_KEY, null,
								com.launchfleet.backend.featureflags.domain.ConditionOperator.EQUALS, List.of("alice"))),
						onVariantId)),
				List.of(new AllocationInput(onVariantId, 10000)), "alice");

		assertThat(request.getProposedConfig().enabled()).isTrue();
		assertThat(request.getProposedConfig().defaultVariantId()).isEqualTo(onVariantId);
		assertThat(request.getProposedConfig().targetingRules()).hasSize(1);
		assertThat(request.getProposedConfig().rollout().allocations()).hasSize(1);
	}

	@Test
	void invalidProposedConfigurationIsRejected() {
		assertThatThrownBy(() -> submitApprovalRequest.execute("acme", "checkout", "production", true,
				"not-a-real-variant", List.of(), List.of(), "alice")).isInstanceOf(ApiException.class);
	}

	@Test
	void retiredFlagCannotReceiveANewProposal() {
		flag.retire("actor");
		featureFlagStore.save(flag);

		assertThatThrownBy(() -> submit("alice")).isInstanceOf(ApiException.class)
				.satisfies(exception -> assertThat(((ApiException) exception).getStatusCode()).isEqualTo(409));
	}

	@Test
	void retiredEnvironmentCannotReceiveANewProposal() {
		environmentLookup.retire(environment.id());

		assertThatThrownBy(() -> submit("alice")).isInstanceOf(ApiException.class)
				.satisfies(exception -> assertThat(((ApiException) exception).getStatusCode()).isEqualTo(409));
	}

	// --- APPROVAL ---

	@Test
	void validAdminApprovalSucceedsAndCreatesANewActiveConfigurationVersion() {
		ApprovalRequest request = submit("alice");

		ApprovalRequest approved = approveApprovalRequest.execute("acme", request.getId(), "admin", "ship it");

		assertThat(approved.getStatus()).isEqualTo(ApprovalStatus.APPLIED);
		assertThat(approved.getAppliedVersion()).isEqualTo(2);
		assertThat(approved.getApprovalComment()).isEqualTo("ship it");
		FeatureFlagConfig active = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flag.getId(),
				environment.id()).orElseThrow();
		assertThat(active.getVersion()).isEqualTo(2);
		assertThat(active.isEnabled()).isTrue();
		assertThat(active.getDefaultVariantId()).isEqualTo(onVariantId);
	}

	@Test
	void approvalCommentIsOptional() {
		ApprovalRequest request = submit("alice");

		ApprovalRequest approved = approveApprovalRequest.execute("acme", request.getId(), "admin", null);

		assertThat(approved.getApprovalComment()).isNull();
		assertThat(approved.getStatus()).isEqualTo(ApprovalStatus.APPLIED);
	}

	@Test
	void submitterCannotApproveTheirOwnRequest() {
		ApprovalRequest request = submit("alice");

		assertThatThrownBy(() -> approveApprovalRequest.execute("acme", request.getId(), "alice", null))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void staleProposalCannotBeApprovedAndBecomesCancelledWithStaleConfigurationReason() {
		ApprovalRequest request = submit("alice");
		// The active config changes after the proposal was created - version moves from 1 to 2.
		FeatureFlagConfig config = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flag.getId(),
				environment.id()).orElseThrow();
		config.updateEnabled(true, "someone-else");
		featureFlagConfigStore.save(config);

		ApprovalRequest result = approveApprovalRequest.execute("acme", request.getId(), "admin", null);

		assertThat(result.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
		assertThat(result.getCancellationReason()).isEqualTo(CancellationReason.STALE_CONFIGURATION);
		// Never applied over the newer configuration - it stays at the version the other edit produced.
		assertThat(featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id())
				.orElseThrow().getVersion()).isEqualTo(2);
	}

	// --- REJECTION ---

	@Test
	void rejectionSucceedsAndIsTerminal() {
		ApprovalRequest request = submit("alice");

		ApprovalRequest rejected = rejectApprovalRequest.execute("acme", request.getId(), "admin", "not aligned");

		assertThat(rejected.getStatus()).isEqualTo(ApprovalStatus.REJECTED);
		assertThat(rejected.getRejectionComment()).isEqualTo("not aligned");
		assertThatThrownBy(() -> approveApprovalRequest.execute("acme", request.getId(), "admin2", null))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void blankRejectionCommentIsRejected() {
		ApprovalRequest request = submit("alice");

		assertThatThrownBy(() -> rejectApprovalRequest.execute("acme", request.getId(), "admin", "   "))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void submitterCannotRejectTheirOwnRequest() {
		ApprovalRequest request = submit("alice");

		assertThatThrownBy(() -> rejectApprovalRequest.execute("acme", request.getId(), "alice", "no"))
				.isInstanceOf(ApiException.class);
	}

	// --- SCHEDULING ---

	@Test
	void futureScheduledTimeIsAcceptedAndBecomesScheduled() {
		ApprovalRequest request = submit("alice");

		ApprovalRequest scheduled = scheduleApprovalRequest.execute("acme", request.getId(), "admin", null,
				clock.instant().plusSeconds(3600));

		assertThat(scheduled.getStatus()).isEqualTo(ApprovalStatus.SCHEDULED);
		assertThat(scheduled.getScheduledAt()).isEqualTo(clock.instant().plusSeconds(3600));
	}

	@Test
	void currentTimeIsRejectedForScheduling() {
		ApprovalRequest request = submit("alice");

		assertThatThrownBy(
				() -> scheduleApprovalRequest.execute("acme", request.getId(), "admin", null, clock.instant()))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void pastTimeIsRejectedForScheduling() {
		ApprovalRequest request = submit("alice");

		assertThatThrownBy(() -> scheduleApprovalRequest.execute("acme", request.getId(), "admin", null,
				clock.instant().minusSeconds(3600))).isInstanceOf(ApiException.class);
	}

	@Test
	void scheduledRequestAutomaticallyAppliesAtDueTimeAndStoresAppliedVersion() {
		ApprovalRequest request = submit("alice");
		scheduleApprovalRequest.execute("acme", request.getId(), "admin", null, clock.instant().plusSeconds(60));

		clock.advance(30);
		int processed = applyDueScheduledApprovals.execute();
		assertThat(processed).isZero();
		assertThat(approvalRequestStore.findById(request.getId()).orElseThrow().getStatus())
				.isEqualTo(ApprovalStatus.SCHEDULED);

		clock.advance(31);
		processed = applyDueScheduledApprovals.execute();

		assertThat(processed).isEqualTo(1);
		ApprovalRequest applied = approvalRequestStore.findById(request.getId()).orElseThrow();
		assertThat(applied.getStatus()).isEqualTo(ApprovalStatus.APPLIED);
		assertThat(applied.getAppliedVersion()).isEqualTo(2);
		assertThat(featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environment.id())
				.orElseThrow().getVersion()).isEqualTo(2);
	}

	@Test
	void staleScheduledRequestBecomesCancelledAtExecutionTime() {
		ApprovalRequest request = submit("alice");
		scheduleApprovalRequest.execute("acme", request.getId(), "admin", null, clock.instant().plusSeconds(60));

		FeatureFlagConfig config = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flag.getId(),
				environment.id()).orElseThrow();
		config.updateEnabled(true, "someone-else");
		featureFlagConfigStore.save(config);

		clock.advance(61);
		applyDueScheduledApprovals.execute();

		ApprovalRequest result = approvalRequestStore.findById(request.getId()).orElseThrow();
		assertThat(result.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
		assertThat(result.getCancellationReason()).isEqualTo(CancellationReason.STALE_CONFIGURATION);
	}

	@Test
	void retiredFlagCausesScheduledCancellationAtExecutionTime() {
		ApprovalRequest request = submit("alice");
		scheduleApprovalRequest.execute("acme", request.getId(), "admin", null, clock.instant().plusSeconds(60));

		flag.retire("actor");
		featureFlagStore.save(flag);

		clock.advance(61);
		applyDueScheduledApprovals.execute();

		ApprovalRequest result = approvalRequestStore.findById(request.getId()).orElseThrow();
		assertThat(result.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
		assertThat(result.getCancellationReason()).isEqualTo(CancellationReason.FLAG_RETIRED);
	}

	@Test
	void retiredEnvironmentCausesScheduledCancellationAtExecutionTime() {
		ApprovalRequest request = submit("alice");
		scheduleApprovalRequest.execute("acme", request.getId(), "admin", null, clock.instant().plusSeconds(60));

		environmentLookup.retire(environment.id());

		clock.advance(61);
		applyDueScheduledApprovals.execute();

		ApprovalRequest result = approvalRequestStore.findById(request.getId()).orElseThrow();
		assertThat(result.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
		assertThat(result.getCancellationReason()).isEqualTo(CancellationReason.ENVIRONMENT_RETIRED);
	}

	// --- CANCELLATION ---

	@Test
	void submitterCanCancelTheirOwnPendingRequest() {
		ApprovalRequest request = submit("alice");

		ApprovalRequest cancelled = cancelApprovalRequest.execute("acme", request.getId(), "alice", false);

		assertThat(cancelled.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
		assertThat(cancelled.getCancellationReason()).isEqualTo(CancellationReason.ADMIN_CANCELLED);
		assertThat(cancelled.getCancelledBy()).isEqualTo("alice");
	}

	@Test
	void submitterCanCancelTheirOwnScheduledRequest() {
		ApprovalRequest request = submit("alice");
		scheduleApprovalRequest.execute("acme", request.getId(), "admin", null, clock.instant().plusSeconds(3600));

		ApprovalRequest cancelled = cancelApprovalRequest.execute("acme", request.getId(), "alice", false);

		assertThat(cancelled.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
	}

	@Test
	void adminCanCancelAScheduledRequestTheyDidNotSubmit() {
		ApprovalRequest request = submit("alice");
		scheduleApprovalRequest.execute("acme", request.getId(), "admin", null, clock.instant().plusSeconds(3600));

		ApprovalRequest cancelled = cancelApprovalRequest.execute("acme", request.getId(), "admin2", true);

		assertThat(cancelled.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
		assertThat(cancelled.getCancelledBy()).isEqualTo("admin2");
	}

	@Test
	void anotherEditorCannotCancelSomeoneElsesPendingRequest() {
		ApprovalRequest request = submit("alice");

		assertThatThrownBy(() -> cancelApprovalRequest.execute("acme", request.getId(), "bob", false))
				.isInstanceOf(ApiException.class)
				.satisfies(exception -> assertThat(((ApiException) exception).getStatusCode()).isEqualTo(403));
	}

	@Test
	void adminCannotCancelSomeoneElsesPendingRequestOnlyScheduled() {
		ApprovalRequest request = submit("alice");

		assertThatThrownBy(() -> cancelApprovalRequest.execute("acme", request.getId(), "admin", true))
				.isInstanceOf(ApiException.class)
				.satisfies(exception -> assertThat(((ApiException) exception).getStatusCode()).isEqualTo(403));
	}

	@Test
	void appliedRequestsCannotBeCancelled() {
		ApprovalRequest request = submit("alice");
		approveApprovalRequest.execute("acme", request.getId(), "admin", null);

		assertThatThrownBy(() -> cancelApprovalRequest.execute("acme", request.getId(), "alice", false))
				.isInstanceOf(ApiException.class);
	}

	@Test
	void rejectedRequestsCannotBeCancelled() {
		ApprovalRequest request = submit("alice");
		rejectApprovalRequest.execute("acme", request.getId(), "admin", "no");

		assertThatThrownBy(() -> cancelApprovalRequest.execute("acme", request.getId(), "alice", false))
				.isInstanceOf(ApiException.class);
	}

	// --- FAILURE ---
	//
	// A pure in-memory "unexpected write failure" test used to live here, using a
	// FeatureFlagConfigStore stub that always throws. It was removed: with the
	// transactional remediation (see ApplyDueScheduledApprovals), that failure needs
	// to happen mid-transaction and be genuinely ROLLED BACK - something
	// NoOpTransactionManager (used by this in-memory suite; see its Javadoc)
	// deliberately does not simulate, since faking real rollback correctly would just
	// be re-implementing MongoTransactionManager for no benefit. That scenario is
	// covered properly, against a real transactional MongoDB, by
	// ApprovalRequestLifecycleConcurrencyTest instead.

	// --- RETIREMENT CASCADE ---

	@Test
	void flagRetirementCancelsPendingAndScheduledRequestsForThatFlag() {
		ApprovalRequest pending = submit("alice");
		FeatureFlag otherFlag = featureFlagStore
				.save(FeatureFlag.create(project.id(), "other-flag", "Other", null, FlagType.BOOLEAN, null, "creator"));
		featureFlagConfigStore.save(FeatureFlagConfig.createDisabled(otherFlag.getId(), environment.id(), project.id(),
				otherFlag.getVariants().get(0).id(), "creator"));
		ApprovalRequest unrelated = submitApprovalRequest.execute("acme", "other-flag", "production", true,
				otherFlag.getVariants().get(1).id(), List.of(), List.of(), "carol");

		flagRetirementListener.onFeatureFlagRetired(project.id(), flag.getId(), "checkout", "actor");

		ApprovalRequest reloaded = approvalRequestStore.findById(pending.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
		assertThat(reloaded.getCancellationReason()).isEqualTo(CancellationReason.FLAG_RETIRED);
		// A request for a different flag is untouched.
		assertThat(approvalRequestStore.findById(unrelated.getId()).orElseThrow().getStatus())
				.isEqualTo(ApprovalStatus.PENDING);
	}

	@Test
	void environmentRetirementCancelsPendingAndScheduledRequestsForThatEnvironment() {
		ApprovalRequest pending = submit("alice");

		environmentRetirementListener.onEnvironmentRetired(project.id(), environment.id(), "production", "actor");

		ApprovalRequest reloaded = approvalRequestStore.findById(pending.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(ApprovalStatus.CANCELLED);
		assertThat(reloaded.getCancellationReason()).isEqualTo(CancellationReason.ENVIRONMENT_RETIRED);
	}

	/** A Clock whose instant can be advanced by hand - deterministic scheduled-execution tests, never a real sleep (locked architecture rule 25). */
	private static final class MutableClock extends Clock {

		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void advance(long seconds) {
			instant = instant.plusSeconds(seconds);
		}

		@Override
		public java.time.ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}

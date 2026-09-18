package com.launchfleet.backend.approvals.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.ApprovalStatus;
import com.launchfleet.backend.approvals.domain.ProposedConfig;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.environments.Environment;
import com.launchfleet.backend.environments.EnvironmentRepository;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.FlagType;
import com.launchfleet.backend.featureflags.ports.EnvironmentLookup;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectRepository;

/**
 * Exercises ApplyDueScheduledApprovals' transactional path against REAL MongoDB (see
 * ApprovalTransactionConfig - requires the configured MongoDB to be a replica set),
 * specifically the FAILED/rollback semantics that a purely in-memory test cannot
 * accurately simulate: ApprovalWorkflowUseCasesTest used to have an equivalent test
 * using NoOpTransactionManager, but that fake cannot actually roll back a write
 * already made in-memory mid-"transaction" (see NoOpTransactionManager's Javadoc) -
 * only a real MongoDB transaction genuinely does. Lives in this package (not
 * security/) specifically to construct its own ApplyProposedConfiguration/
 * ApplyDueScheduledApprovals wired with a throwing FeatureFlagConfigStore decorator,
 * both of which are package-private by design.
 */
@SpringBootTest
class ScheduledApplicationRealTransactionTest {

	private static final String PROJECT_KEY = "sched-real-txn-test";

	private static final String ENVIRONMENT_KEY = "production";

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private EnvironmentRepository environmentRepository;

	@Autowired
	private FeatureFlagStore featureFlagStore;

	@Autowired
	private FeatureFlagConfigStore featureFlagConfigStore;

	@Autowired
	private EnvironmentLookup environmentLookup;

	@Autowired
	private ApprovalRequestStore approvalRequestStore;

	@Autowired
	private TransactionTemplate scheduledApprovalTransactionTemplate;

	private String flagId;

	private String environmentId;

	private FeatureFlagConfig configBeforeAnyAttempt;

	@BeforeEach
	void setUp() {
		approvalRequestStore.deleteAll();
		featureFlagConfigStore.deleteAll();
		featureFlagStore.deleteAll();
		environmentRepository.deleteAll();
		projectRepository.deleteAll();

		Project project = new Project();
		project.setKey(PROJECT_KEY);
		project.setName(PROJECT_KEY);
		Project savedProject = projectRepository.save(project);

		Environment environment = new Environment();
		environment.setProjectId(savedProject.getId());
		environment.setKey(ENVIRONMENT_KEY);
		environment.setName("Production");
		Environment savedEnvironment = environmentRepository.save(environment);
		environmentId = savedEnvironment.getId();

		FeatureFlag flag = featureFlagStore
				.save(FeatureFlag.create(savedProject.getId(), "sched-real-txn-flag", "Flag", null, FlagType.BOOLEAN,
						null, "creator"));
		flagId = flag.getId();
		configBeforeAnyAttempt = featureFlagConfigStore.save(FeatureFlagConfig.createDisabled(flagId, environmentId,
				savedProject.getId(), flag.getVariants().get(0).id(), "creator"));
	}

	@Test
	void anUnexpectedFailureRollsBackTheEntireTransactionLeavingBothAggregatesUnchangedAndMarksFailed() {
		String onVariantId = featureFlagStore.findById(flagId).orElseThrow().getVariants().get(1).id();
		ProposedConfig proposedConfig = new ProposedConfig(true, onVariantId, List.of(), null);
		ApprovalRequest request = ApprovalRequest.propose(configBeforeAnyAttempt.getProjectId(), flagId, environmentId,
				configBeforeAnyAttempt.getVersion(), proposedConfig, "alice");
		request = approvalRequestStore.save(request);
		request.approve("admin", null);
		request = approvalRequestStore.save(request);
		request.scheduleFor(Instant.now().plusSeconds(60), Instant.now());
		request = approvalRequestStore.save(request);
		String requestId = request.getId();

		// A decorator that fails only the write half, after everything up to that point
		// (the claim, the flag/environment/version checks, the in-memory domain mutation)
		// has already happened exactly as it would in production.
		ThrowingFeatureFlagConfigStore throwingStore = new ThrowingFeatureFlagConfigStore(featureFlagConfigStore);
		ApplyProposedConfiguration throwingApply = new ApplyProposedConfiguration(featureFlagStore, throwingStore,
				environmentLookup);
		ApplyDueScheduledApprovals throwingScheduler = new ApplyDueScheduledApprovals(approvalRequestStore,
				throwingApply, Clock.fixed(Instant.now().plusSeconds(120), java.time.ZoneOffset.UTC),
				scheduledApprovalTransactionTemplate);

		throwingScheduler.execute();

		ApprovalRequest afterFailure = approvalRequestStore.findById(requestId).orElseThrow();
		assertThat(afterFailure.getStatus()).isEqualTo(ApprovalStatus.FAILED);

		// The real, defining assertion: the FeatureFlagConfig write that happened INSIDE
		// the failed transaction was genuinely rolled back by MongoDB, not just left
		// alone by application logic that never attempted it - version is untouched.
		FeatureFlagConfig configAfterFailure = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId).orElseThrow();
		assertThat(configAfterFailure.getVersion()).isEqualTo(configBeforeAnyAttempt.getVersion());
		assertThat(configAfterFailure.isEnabled()).isEqualTo(configBeforeAnyAttempt.isEnabled());

		// No automatic retry: a real (working) poller must not pick this request up again.
		Optional<ApprovalRequest> stillDue = approvalRequestStore.findScheduledAtOrBefore(Instant.now().plusSeconds(120))
				.stream().filter(r -> r.getId().equals(requestId)).findFirst();
		assertThat(stillDue).isEmpty();
		assertThat(approvalRequestStore.findById(requestId).orElseThrow().getStatus()).isEqualTo(ApprovalStatus.FAILED);
	}

	/** Delegates everything except applyIfCurrentVersion, which always throws - simulates an unexpected write failure mid-transaction. */
	private static final class ThrowingFeatureFlagConfigStore implements FeatureFlagConfigStore {

		private final FeatureFlagConfigStore delegate;

		ThrowingFeatureFlagConfigStore(FeatureFlagConfigStore delegate) {
			this.delegate = delegate;
		}

		@Override
		public List<FeatureFlagConfig> findByFeatureFlagId(String featureFlagId) {
			return delegate.findByFeatureFlagId(featureFlagId);
		}

		@Override
		public Optional<FeatureFlagConfig> findByFeatureFlagIdAndEnvironmentId(String featureFlagId,
				String environmentId) {
			return delegate.findByFeatureFlagIdAndEnvironmentId(featureFlagId, environmentId);
		}

		@Override
		public List<FeatureFlagConfig> findByProjectId(String projectId) {
			return delegate.findByProjectId(projectId);
		}

		@Override
		public FeatureFlagConfig save(FeatureFlagConfig config) {
			return delegate.save(config);
		}

		@Override
		public Optional<FeatureFlagConfig> applyIfCurrentVersion(FeatureFlagConfig desiredState, int expectedVersion) {
			throw new IllegalStateException("simulated unexpected write failure");
		}

		@Override
		public void deleteAll() {
			delegate.deleteAll();
		}
	}
}

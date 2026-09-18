package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.launchfleet.backend.approvals.application.ApplyDueScheduledApprovals;
import com.launchfleet.backend.approvals.domain.ApprovalRequest;
import com.launchfleet.backend.approvals.domain.ApprovalStatus;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.environments.Environment;
import com.launchfleet.backend.environments.EnvironmentRepository;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectMembership;
import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.users.Role;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserRepository;

import jakarta.servlet.http.Cookie;

/**
 * Real, latch-coordinated concurrency coverage for ApprovalRequest LIFECYCLE
 * persistence (as distinct from ApprovalRequestApiTest's one-PENDING-per-scope
 * concurrency test, which only proves the submission-time unique index). Every test
 * here starts two threads from a shared CountDownLatch so both requests are genuinely
 * in flight together, then asserts: exactly one operation actually won, the loser
 * observed a conflict (never silently overwritten the winner's state), and - where
 * FeatureFlagConfig is involved - it was mutated at most once. Real (Dockerized)
 * MongoDB, real HTTP, same style as ApprovalRequestApiTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApprovalRequestLifecycleConcurrencyTest {

	private static final String PROJECT_KEY = "approval-concurrency-test";

	private static final String ENVIRONMENT_KEY = "production";

	private static final String PASSWORD = "correct horse battery staple";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectMembershipRepository projectMembershipRepository;

	@Autowired
	private ProjectRepository projectRepository;

	@Autowired
	private EnvironmentRepository environmentRepository;

	@Autowired
	private FeatureFlagStore featureFlagStore;

	@Autowired
	private FeatureFlagConfigStore featureFlagConfigStore;

	@Autowired
	private ApprovalRequestStore approvalRequestStore;

	@Autowired
	private ApplyDueScheduledApprovals applyDueScheduledApprovals;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		approvalRequestStore.deleteAll();
		featureFlagConfigStore.deleteAll();
		featureFlagStore.deleteAll();
		environmentRepository.deleteAll();
		projectRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();

		Project project = new Project();
		project.setKey(PROJECT_KEY);
		project.setName(PROJECT_KEY);
		projectRepository.save(project);

		Environment environment = new Environment();
		environment.setProjectId(project.getId());
		environment.setKey(ENVIRONMENT_KEY);
		environment.setName("Production");
		environmentRepository.save(environment);
	}

	@Test
	void concurrentApproveVsRejectOnTheSamePendingRequestHasExactlyOneWinner() throws Exception {
		Cookie editorSession = loginAs("editor-avr@example.com", Role.EDITOR);
		String variantId = createFlagAndCaptureOnVariantId(editorSession, "avr-flag");
		String requestId = submit(editorSession, "avr-flag", variantId);

		Cookie admin1 = loginAs("admin-avr-1@example.com", Role.ADMIN);
		Cookie admin2 = loginAs("admin-avr-2@example.com", Role.ADMIN);

		int[] approveStatus = new int[1];
		int[] rejectStatus = new int[1];
		runConcurrently(
				() -> approveStatus[0] = perform(withCsrf(post(approvePath(requestId)), admin1)
						.contentType(MediaType.APPLICATION_JSON).content("{}")),
				() -> rejectStatus[0] = perform(withCsrf(post(rejectPath(requestId)), admin2)
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"rejectionComment":"racing rejection"}""")));

		assertExactlyOneWinner(approveStatus[0], rejectStatus[0]);

		ApprovalRequest finalState = approvalRequestStore.findById(requestId).orElseThrow();
		assertThat(finalState.getStatus()).isIn(ApprovalStatus.APPLIED, ApprovalStatus.REJECTED);
		if (finalState.getStatus() == ApprovalStatus.APPLIED) {
			assertThat(approveStatus[0]).isEqualTo(200);
			assertThat(rejectStatus[0]).isEqualTo(409);
		} else {
			assertThat(rejectStatus[0]).isEqualTo(200);
			assertThat(approveStatus[0]).isEqualTo(409);
		}
	}

	@Test
	void concurrentApproveVsCancelOnTheSamePendingRequestHasExactlyOneWinner() throws Exception {
		Cookie editorSession = loginAs("editor-avc@example.com", Role.EDITOR);
		String variantId = createFlagAndCaptureOnVariantId(editorSession, "avc-flag");
		String requestId = submit(editorSession, "avc-flag", variantId);

		Cookie admin = loginAs("admin-avc@example.com", Role.ADMIN);

		int[] approveStatus = new int[1];
		int[] cancelStatus = new int[1];
		runConcurrently(
				() -> approveStatus[0] = perform(withCsrf(post(approvePath(requestId)), admin)
						.contentType(MediaType.APPLICATION_JSON).content("{}")),
				() -> cancelStatus[0] = perform(withCsrf(post(cancelPath(requestId)), editorSession)));

		assertExactlyOneWinner(approveStatus[0], cancelStatus[0]);

		ApprovalRequest finalState = approvalRequestStore.findById(requestId).orElseThrow();
		assertThat(finalState.getStatus()).isIn(ApprovalStatus.APPLIED, ApprovalStatus.CANCELLED);
	}

	@Test
	void concurrentDuplicateApproveAttemptsApplyTheConfigurationExactlyOnce() throws Exception {
		Cookie editorSession = loginAs("editor-dup@example.com", Role.EDITOR);
		String flagId = createFlagAndCaptureFlagId(editorSession, "dup-approve-flag");
		String variantId = onVariantIdOf(flagId);
		String environmentId = environmentRepository.findByProjectIdAndKey(projectRepository.findByKey(PROJECT_KEY)
				.orElseThrow().getId(), ENVIRONMENT_KEY).orElseThrow().getId();
		int versionBefore = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId)
				.orElseThrow().getVersion();
		String requestId = submit(editorSession, "dup-approve-flag", variantId);

		Cookie admin1 = loginAs("admin-dup-1@example.com", Role.ADMIN);
		Cookie admin2 = loginAs("admin-dup-2@example.com", Role.ADMIN);

		int[] status1 = new int[1];
		int[] status2 = new int[1];
		runConcurrently(
				() -> status1[0] = perform(withCsrf(post(approvePath(requestId)), admin1)
						.contentType(MediaType.APPLICATION_JSON).content("{}")),
				() -> status2[0] = perform(withCsrf(post(approvePath(requestId)), admin2)
						.contentType(MediaType.APPLICATION_JSON).content("{}")));

		assertExactlyOneWinner(status1[0], status2[0]);

		ApprovalRequest finalState = approvalRequestStore.findById(requestId).orElseThrow();
		assertThat(finalState.getStatus()).isEqualTo(ApprovalStatus.APPLIED);
		assertThat(finalState.getAppliedVersion()).isEqualTo(versionBefore + 1);

		// The config incremented exactly once - not twice, not zero times.
		int versionAfter = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId)
				.orElseThrow().getVersion();
		assertThat(versionAfter).isEqualTo(versionBefore + 1);
	}

	@Test
	void concurrentSchedulerExecutionsAgainstTheSameScheduledRequestApplyExactlyOnce() throws Exception {
		Cookie editorSession = loginAs("editor-sched@example.com", Role.EDITOR);
		String flagId = createFlagAndCaptureFlagId(editorSession, "sched-race-flag");
		String variantId = onVariantIdOf(flagId);
		String environmentId = environmentRepository.findByProjectIdAndKey(projectRepository.findByKey(PROJECT_KEY)
				.orElseThrow().getId(), ENVIRONMENT_KEY).orElseThrow().getId();
		int versionBefore = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId)
				.orElseThrow().getVersion();
		String requestId = submit(editorSession, "sched-race-flag", variantId);

		Cookie admin = loginAs("admin-sched@example.com", Role.ADMIN);
		mockMvc.perform(withCsrf(post(schedulePath(requestId)), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"scheduledAt\":\"" + Instant.now().plusMillis(300) + "\"}")).andExpect(status().isOk());
		Thread.sleep(400);

		runConcurrently(applyDueScheduledApprovals::execute, applyDueScheduledApprovals::execute);

		ApprovalRequest finalState = approvalRequestStore.findById(requestId).orElseThrow();
		assertThat(finalState.getStatus()).isEqualTo(ApprovalStatus.APPLIED);
		assertThat(finalState.getAppliedVersion()).isEqualTo(versionBefore + 1);

		int versionAfter = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId)
				.orElseThrow().getVersion();
		assertThat(versionAfter).isEqualTo(versionBefore + 1);

		// A later execution (after both racing ones already ran) must not find - and
		// therefore cannot revert - anything: the request is no longer SCHEDULED.
		int laterProcessedCount = applyDueScheduledApprovals.execute();
		assertThat(laterProcessedCount).isZero();
		assertThat(approvalRequestStore.findById(requestId).orElseThrow().getStatus()).isEqualTo(ApprovalStatus.APPLIED);
	}

	@Test
	void schedulerExecutionRacingManualCancellationHasExactlyOneWinner() throws Exception {
		Cookie editorSession = loginAs("editor-svc@example.com", Role.EDITOR);
		String variantId = createFlagAndCaptureOnVariantId(editorSession, "sched-vs-cancel-flag");
		String requestId = submit(editorSession, "sched-vs-cancel-flag", variantId);

		Cookie admin = loginAs("admin-svc@example.com", Role.ADMIN);
		mockMvc.perform(withCsrf(post(schedulePath(requestId)), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"scheduledAt\":\"" + Instant.now().plusMillis(300) + "\"}")).andExpect(status().isOk());
		Thread.sleep(400);

		int[] cancelStatus = new int[1];
		runConcurrently(applyDueScheduledApprovals::execute,
				() -> cancelStatus[0] = perform(withCsrf(post(cancelPath(requestId)), editorSession)));

		ApprovalRequest finalState = approvalRequestStore.findById(requestId).orElseThrow();
		assertThat(finalState.getStatus()).isIn(ApprovalStatus.APPLIED, ApprovalStatus.CANCELLED);
		if (finalState.getStatus() == ApprovalStatus.CANCELLED) {
			assertThat(cancelStatus[0]).isEqualTo(200);
		} else {
			// The scheduler won: the manual cancel must never have silently succeeded
			// against an already-APPLIED request. Depending on exactly when its own fresh
			// read landed, it either lost the persistenceVersion race (409, if it read
			// before the scheduler's save but tried to save after) or correctly saw the
			// already-APPLIED status and refused on that basis (403, CancelApprovalRequest's
			// own authorization check) - both are valid non-overwriting outcomes.
			assertThat(cancelStatus[0]).isIn(403, 409);
		}
	}

	@Test
	void flagRetirementRacingAnApprovalHasExactlyOneWinnerAndNeverBothMutatesConfigAndCancels() throws Exception {
		Cookie editorSession = loginAs("editor-retire@example.com", Role.EDITOR);
		String flagId = createFlagAndCaptureFlagId(editorSession, "retire-race-flag");
		String variantId = onVariantIdOf(flagId);
		String environmentId = environmentRepository.findByProjectIdAndKey(projectRepository.findByKey(PROJECT_KEY)
				.orElseThrow().getId(), ENVIRONMENT_KEY).orElseThrow().getId();
		int versionBefore = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId)
				.orElseThrow().getVersion();
		String requestId = submit(editorSession, "retire-race-flag", variantId);

		Cookie admin = loginAs("admin-retire@example.com", Role.ADMIN);

		int[] approveStatus = new int[1];
		runConcurrently(
				() -> approveStatus[0] = perform(withCsrf(post(approvePath(requestId)), admin)
						.contentType(MediaType.APPLICATION_JSON).content("{}")),
				() -> perform(withCsrf(post(flagsPath() + "/retire-race-flag/retire"), editorSession)));

		ApprovalRequest finalState = approvalRequestStore.findById(requestId).orElseThrow();
		int versionAfter = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId)
				.orElseThrow().getVersion();

		assertThat(finalState.getStatus()).isIn(ApprovalStatus.APPLIED, ApprovalStatus.CANCELLED);
		if (finalState.getStatus() == ApprovalStatus.APPLIED) {
			// Approval won the race before the retirement's cascade reached this request -
			// the config change is real and the request record agrees with it.
			assertThat(approveStatus[0]).isEqualTo(200);
			assertThat(versionAfter).isEqualTo(versionBefore + 1);
		} else {
			// Retirement's cascade won - the approval must not have silently mutated the
			// configuration underneath a request that is now recorded as cancelled.
			assertThat(versionAfter).isEqualTo(versionBefore);
		}
	}

	@Test
	void scheduledApplicationVsFlagRetirementHasExactlyOneWinnerAndNeverMutatesConfigWhenRetirementWins()
			throws Exception {
		Cookie editorSession = loginAs("editor-sfr@example.com", Role.EDITOR);
		String flagId = createFlagAndCaptureFlagId(editorSession, "sched-flag-retire-flag");
		String variantId = onVariantIdOf(flagId);
		String environmentId = environmentRepository.findByProjectIdAndKey(projectRepository.findByKey(PROJECT_KEY)
				.orElseThrow().getId(), ENVIRONMENT_KEY).orElseThrow().getId();
		int versionBefore = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId)
				.orElseThrow().getVersion();
		String requestId = submit(editorSession, "sched-flag-retire-flag", variantId);

		Cookie admin = loginAs("admin-sfr@example.com", Role.ADMIN);
		mockMvc.perform(withCsrf(post(schedulePath(requestId)), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"scheduledAt\":\"" + Instant.now().plusMillis(300) + "\"}")).andExpect(status().isOk());
		Thread.sleep(400);

		// This is the exact race this remediation exists for: the scheduler's
		// transactional apply racing a flag retirement's cascade cancellation of the
		// same SCHEDULED request.
		runConcurrently(applyDueScheduledApprovals::execute,
				() -> perform(withCsrf(post(flagsPath() + "/sched-flag-retire-flag/retire"), editorSession)));

		ApprovalRequest finalState = approvalRequestStore.findById(requestId).orElseThrow();
		int versionAfter = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId)
				.orElseThrow().getVersion();

		assertThat(finalState.getStatus()).isIn(ApprovalStatus.APPLIED, ApprovalStatus.CANCELLED);
		if (finalState.getStatus() == ApprovalStatus.APPLIED) {
			assertThat(finalState.getAppliedVersion()).isEqualTo(versionBefore + 1);
			assertThat(versionAfter).isEqualTo(versionBefore + 1);
		} else {
			// The invariant this whole remediation exists to prove: if retirement won,
			// the scheduler's transaction was rolled back in full - the configuration was
			// NEVER mutated, not even transiently. No CANCELLED-request-with-applied-config
			// state is reachable.
			assertThat(finalState.getCancellationReason()).isNotNull();
			assertThat(versionAfter).isEqualTo(versionBefore);
		}

		// A later poll must never revert whichever outcome won.
		applyDueScheduledApprovals.execute();
		assertThat(approvalRequestStore.findById(requestId).orElseThrow().getStatus()).isEqualTo(finalState.getStatus());
	}

	@Test
	void scheduledApplicationVsEnvironmentRetirementHasExactlyOneWinnerAndNeverMutatesConfigWhenRetirementWins()
			throws Exception {
		Cookie editorSession = loginAs("editor-ser@example.com", Role.EDITOR);
		String flagId = createFlagAndCaptureFlagId(editorSession, "sched-env-retire-flag");
		String variantId = onVariantIdOf(flagId);
		Project project = projectRepository.findByKey(PROJECT_KEY).orElseThrow();
		String environmentId = environmentRepository.findByProjectIdAndKey(project.getId(), ENVIRONMENT_KEY)
				.orElseThrow().getId();
		int versionBefore = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId)
				.orElseThrow().getVersion();
		String requestId = submit(editorSession, "sched-env-retire-flag", variantId);

		Cookie admin = loginAs("admin-ser@example.com", Role.ADMIN);
		mockMvc.perform(withCsrf(post(schedulePath(requestId)), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"scheduledAt\":\"" + Instant.now().plusMillis(300) + "\"}")).andExpect(status().isOk());
		Thread.sleep(400);

		runConcurrently(applyDueScheduledApprovals::execute,
				() -> perform(withCsrf(post(environmentRetirePath()), editorSession)));

		ApprovalRequest finalState = approvalRequestStore.findById(requestId).orElseThrow();
		int versionAfter = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId)
				.orElseThrow().getVersion();

		assertThat(finalState.getStatus()).isIn(ApprovalStatus.APPLIED, ApprovalStatus.CANCELLED);
		if (finalState.getStatus() == ApprovalStatus.APPLIED) {
			assertThat(versionAfter).isEqualTo(versionBefore + 1);
		} else {
			assertThat(versionAfter).isEqualTo(versionBefore);
		}
	}

	@Test
	void retirementAfterASuccessfullyAppliedScheduledRequestDoesNotCancelIt() throws Exception {
		Cookie editorSession = loginAs("editor-raa@example.com", Role.EDITOR);
		String flagId = createFlagAndCaptureFlagId(editorSession, "retire-after-apply-flag");
		String variantId = onVariantIdOf(flagId);
		String environmentId = environmentRepository.findByProjectIdAndKey(projectRepository.findByKey(PROJECT_KEY)
				.orElseThrow().getId(), ENVIRONMENT_KEY).orElseThrow().getId();
		int versionBefore = featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId)
				.orElseThrow().getVersion();
		String requestId = submit(editorSession, "retire-after-apply-flag", variantId);

		Cookie admin = loginAs("admin-raa@example.com", Role.ADMIN);
		mockMvc.perform(withCsrf(post(schedulePath(requestId)), admin).contentType(MediaType.APPLICATION_JSON)
				.content("{\"scheduledAt\":\"" + Instant.now().plusMillis(300) + "\"}")).andExpect(status().isOk());
		Thread.sleep(400);

		// Uncontested: the scheduled application commits fully before retirement ever runs.
		applyDueScheduledApprovals.execute();
		ApprovalRequest afterApply = approvalRequestStore.findById(requestId).orElseThrow();
		assertThat(afterApply.getStatus()).isEqualTo(ApprovalStatus.APPLIED);
		assertThat(afterApply.getAppliedVersion()).isEqualTo(versionBefore + 1);

		mockMvc.perform(withCsrf(post(flagsPath() + "/retire-after-apply-flag/retire"), editorSession))
				.andExpect(status().isOk());

		// The retirement cascade's own PENDING/SCHEDULED query never even considers an
		// already-APPLIED request - nothing to race, nothing to revert.
		ApprovalRequest afterRetirement = approvalRequestStore.findById(requestId).orElseThrow();
		assertThat(afterRetirement.getStatus()).isEqualTo(ApprovalStatus.APPLIED);
		assertThat(afterRetirement.getAppliedVersion()).isEqualTo(versionBefore + 1);
		assertThat(featureFlagConfigStore.findByFeatureFlagIdAndEnvironmentId(flagId, environmentId).orElseThrow()
				.getVersion()).isEqualTo(versionBefore + 1);
	}

	private void assertExactlyOneWinner(int statusA, int statusB) {
		boolean aWon = statusA == 200;
		boolean bWon = statusB == 200;
		assertThat(aWon ^ bWon).as("exactly one of the two racing operations should have succeeded (200): got %s and %s",
				statusA, statusB).isTrue();
		int loserStatus = aWon ? statusB : statusA;
		assertThat(loserStatus).as("the loser must observe a 409 conflict, not some other error").isEqualTo(409);
	}

	/** Two actions launched from a shared latch so both are genuinely in flight together - never sleeps as the synchronization mechanism. */
	private void runConcurrently(ThrowingRunnable first, ThrowingRunnable second) throws Exception {
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch go = new CountDownLatch(1);
		AtomicInteger failures = new AtomicInteger();

		Thread t1 = new Thread(() -> runGated(first, ready, go, failures));
		Thread t2 = new Thread(() -> runGated(second, ready, go, failures));
		t1.start();
		t2.start();
		ready.await();
		go.countDown();
		t1.join();
		t2.join();

		assertThat(failures.get()).as("neither racing operation should throw an unexpected exception").isZero();
	}

	private void runGated(ThrowingRunnable action, CountDownLatch ready, CountDownLatch go, AtomicInteger failures) {
		ready.countDown();
		try {
			go.await();
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			return;
		}
		try {
			action.run();
		} catch (Exception exception) {
			failures.incrementAndGet();
		}
	}

	@FunctionalInterface
	private interface ThrowingRunnable {
		void run() throws Exception;
	}

	private int perform(MockHttpServletRequestBuilder builder) {
		try {
			return mockMvc.perform(builder).andReturn().getResponse().getStatus();
		} catch (Exception exception) {
			throw new RuntimeException(exception);
		}
	}

	private String submit(Cookie editorSession, String flagKey, String variantId) throws Exception {
		MvcResult submitted = mockMvc
				.perform(withCsrf(post(flagsPath() + "/" + flagKey + "/environments/" + ENVIRONMENT_KEY
						+ "/approval-requests"), editorSession).contentType(MediaType.APPLICATION_JSON).content("""
						{"enabled":true,"defaultVariantId":"%s","targetingRules":[],"rollout":null}""".formatted(variantId)))
				.andExpect(status().isOk()).andReturn();

		return JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.id");
	}

	private String createFlagAndCaptureOnVariantId(Cookie session, String flagKey) throws Exception {
		MvcResult created = mockMvc
				.perform(withCsrf(post(flagsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
						{"key":"%s","name":"%s","type":"BOOLEAN"}""".formatted(flagKey, flagKey)))
				.andExpect(status().isOk()).andReturn();

		return JsonPath.read(created.getResponse().getContentAsString(), "$.data.variants[1].id");
	}

	private String createFlagAndCaptureFlagId(Cookie session, String flagKey) throws Exception {
		MvcResult created = mockMvc
				.perform(withCsrf(post(flagsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
						{"key":"%s","name":"%s","type":"BOOLEAN"}""".formatted(flagKey, flagKey)))
				.andExpect(status().isOk()).andReturn();

		return JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
	}

	private String onVariantIdOf(String flagId) {
		return featureFlagStore.findById(flagId).orElseThrow().getVariants().get(1).id();
	}

	private String flagsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/flags";
	}

	private String environmentRetirePath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/environments/" + ENVIRONMENT_KEY + "/retire";
	}

	private String approvalRequestsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/approval-requests";
	}

	private String approvePath(String requestId) {
		return approvalRequestsPath() + "/" + requestId + "/approve";
	}

	private String rejectPath(String requestId) {
		return approvalRequestsPath() + "/" + requestId + "/reject";
	}

	private String schedulePath(String requestId) {
		return approvalRequestsPath() + "/" + requestId + "/schedule";
	}

	private String cancelPath(String requestId) {
		return approvalRequestsPath() + "/" + requestId + "/cancel";
	}

	private Cookie loginAs(String email, Role role) throws Exception {
		User user = new User();
		user.setName(email);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setActive(true);
		User saved = userRepository.save(user);

		ProjectMembership membership = new ProjectMembership();
		membership.setUserId(saved.getId());
		membership.setProjectKey(PROJECT_KEY);
		membership.setRole(role);
		projectMembershipRepository.save(membership);

		Cookie[] csrf = obtainCsrfCookie();
		MvcResult result = mockMvc
				.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.cookie(csrf).header("X-XSRF-TOKEN", csrf[0].getValue())
						.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk()).andReturn();

		return result.getResponse().getCookie(SecurityConstants.SESSION_COOKIE_NAME);
	}

	private Cookie[] obtainCsrfCookie() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/auth/session")).andReturn();
		Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).isNotNull();

		return new Cookie[] { csrf };
	}

	private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder, Cookie session)
			throws Exception {
		Cookie[] csrf = obtainCsrfCookie();

		return builder.cookie(session, csrf[0]).header("X-XSRF-TOKEN", csrf[0].getValue());
	}
}

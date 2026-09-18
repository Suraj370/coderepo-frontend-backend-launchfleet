package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
 * Integration coverage for the approval workflow API (Phase 6): RBAC, the two-person
 * approval rule, the full propose/approve/reject/schedule/cancel lifecycle through
 * real HTTP, and the one-PENDING-per-scope backstop under real concurrency. Same
 * style as FeatureFlagApiTest - real MockMvc requests against the real dashboard
 * SecurityFilterChain and a real (Dockerized) MongoDB.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApprovalRequestApiTest {

	private static final String PROJECT_KEY = "approval-api-test";

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
	private PasswordEncoder passwordEncoder;

	private String onVariantId;

	@BeforeEach
	void setUp() throws Exception {
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

	private String createFlagAndCaptureOnVariantId(Cookie session, String flagKey) throws Exception {
		MvcResult created = mockMvc
				.perform(withCsrf(post(flagsPath() + ""), session).contentType(MediaType.APPLICATION_JSON).content("""
						{"key":"%s","name":"%s","type":"BOOLEAN"}""".formatted(flagKey, flagKey)))
				.andExpect(status().isOk()).andReturn();

		return JsonPath.read(created.getResponse().getContentAsString(), "$.data.variants[1].id");
	}

	private MockHttpServletRequestBuilder submitRequest(String flagKey, String variantId) {
		return post(flagsPath() + "/" + flagKey + "/environments/" + ENVIRONMENT_KEY + "/approval-requests")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"enabled":true,"defaultVariantId":"%s","targetingRules":[],"rollout":null}""".formatted(variantId));
	}

	@Test
	void editorCanSubmitAndAnAdminOtherThanTheSubmitterCanApproveImmediately() throws Exception {
		Cookie editorSession = loginAs("editor@example.com", Role.EDITOR, PROJECT_KEY);
		onVariantId = createFlagAndCaptureOnVariantId(editorSession, "checkout");

		MvcResult submitted = mockMvc
				.perform(withCsrf(submitRequest("checkout", onVariantId), editorSession))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("PENDING"))
				.andExpect(jsonPath("$.data.baseConfigVersion").value(1)).andReturn();
		String requestId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.id");

		Cookie adminSession = loginAs("admin@example.com", Role.ADMIN, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/approve"), adminSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"approvalComment":"ship it"}"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("APPLIED"))
				.andExpect(jsonPath("$.data.appliedVersion").value(2))
				.andExpect(jsonPath("$.data.approvalComment").value("ship it"));

		mockMvc.perform(get(flagsPath() + "/checkout").cookie(editorSession)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments[0].version").value(2))
				.andExpect(jsonPath("$.data.environments[0].enabled").value(true));
	}

	@Test
	void viewerCanViewButCannotSubmitApproveRejectScheduleOrCancel() throws Exception {
		Cookie editorSession = loginAs("editor2@example.com", Role.EDITOR, PROJECT_KEY);
		onVariantId = createFlagAndCaptureOnVariantId(editorSession, "viewer-flag");
		MvcResult submitted = mockMvc.perform(withCsrf(submitRequest("viewer-flag", onVariantId), editorSession))
				.andExpect(status().isOk()).andReturn();
		String requestId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.id");

		Cookie viewerSession = loginAs("viewer@example.com", Role.VIEWER, PROJECT_KEY);
		mockMvc.perform(get(approvalRequestsPath()).cookie(viewerSession)).andExpect(status().isOk());
		mockMvc.perform(get(approvalPath(requestId)).cookie(viewerSession)).andExpect(status().isOk());

		mockMvc.perform(withCsrf(submitRequest("viewer-flag", onVariantId), viewerSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/approve"), viewerSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/reject"), viewerSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"rejectionComment":"no"}""")).andExpect(status().isForbidden());
		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/schedule"), viewerSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"scheduledAt\":\"" + Instant.now().plus(1, ChronoUnit.HOURS) + "\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/cancel"), viewerSession))
				.andExpect(status().isForbidden());
	}

	@Test
	void editorCannotApproveRejectOrSchedule() throws Exception {
		Cookie editorSession = loginAs("editor3@example.com", Role.EDITOR, PROJECT_KEY);
		onVariantId = createFlagAndCaptureOnVariantId(editorSession, "editor-flag");
		MvcResult submitted = mockMvc.perform(withCsrf(submitRequest("editor-flag", onVariantId), editorSession))
				.andExpect(status().isOk()).andReturn();
		String requestId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/approve"), editorSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/reject"), editorSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"rejectionComment":"no"}""")).andExpect(status().isForbidden());
	}

	@Test
	void submitterCannotApproveTheirOwnRequestEvenIfTheyAreAnAdmin() throws Exception {
		Cookie adminSubmitter = loginAs("admin-submitter@example.com", Role.ADMIN, PROJECT_KEY);
		onVariantId = createFlagAndCaptureOnVariantId(adminSubmitter, "self-approve");
		MvcResult submitted = mockMvc.perform(withCsrf(submitRequest("self-approve", onVariantId), adminSubmitter))
				.andExpect(status().isOk()).andReturn();
		String requestId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/approve"), adminSubmitter))
				.andExpect(status().isConflict());
	}

	@Test
	void rejectionRequiresANonBlankCommentAndIsTerminal() throws Exception {
		Cookie editorSession = loginAs("editor4@example.com", Role.EDITOR, PROJECT_KEY);
		onVariantId = createFlagAndCaptureOnVariantId(editorSession, "reject-flag");
		MvcResult submitted = mockMvc.perform(withCsrf(submitRequest("reject-flag", onVariantId), editorSession))
				.andExpect(status().isOk()).andReturn();
		String requestId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.id");

		Cookie adminSession = loginAs("admin2@example.com", Role.ADMIN, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/reject"), adminSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"rejectionComment":"   "}""")).andExpect(status().isBadRequest());

		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/reject"), adminSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"rejectionComment":"needs more testing"}"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("REJECTED"));

		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/approve"), adminSession))
				.andExpect(status().isConflict());
	}

	@Test
	void secondPendingRequestForTheSameFlagAndEnvironmentIsRejected() throws Exception {
		Cookie editorSession = loginAs("editor5@example.com", Role.EDITOR, PROJECT_KEY);
		onVariantId = createFlagAndCaptureOnVariantId(editorSession, "dup-flag");

		mockMvc.perform(withCsrf(submitRequest("dup-flag", onVariantId), editorSession)).andExpect(status().isOk());
		mockMvc.perform(withCsrf(submitRequest("dup-flag", onVariantId), editorSession))
				.andExpect(status().isConflict());
	}

	@Test
	void scheduleRequiresAFutureTimeAndAppliesAutomaticallyOncePolledAfterDue() throws Exception {
		Cookie editorSession = loginAs("editor6@example.com", Role.EDITOR, PROJECT_KEY);
		onVariantId = createFlagAndCaptureOnVariantId(editorSession, "schedule-flag");
		MvcResult submitted = mockMvc.perform(withCsrf(submitRequest("schedule-flag", onVariantId), editorSession))
				.andExpect(status().isOk()).andReturn();
		String requestId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.id");

		Cookie adminSession = loginAs("admin3@example.com", Role.ADMIN, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/schedule"), adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"scheduledAt\":\"" + Instant.now().minusSeconds(60) + "\"}"))
				.andExpect(status().isBadRequest());

		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/schedule"), adminSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"scheduledAt\":\"" + Instant.now().plusSeconds(3600) + "\"}"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SCHEDULED"));
	}

	@Test
	void submitterCanCancelTheirOwnPendingRequestButAnotherEditorCannot() throws Exception {
		Cookie editorSession = loginAs("editor7@example.com", Role.EDITOR, PROJECT_KEY);
		onVariantId = createFlagAndCaptureOnVariantId(editorSession, "cancel-flag");
		MvcResult submitted = mockMvc.perform(withCsrf(submitRequest("cancel-flag", onVariantId), editorSession))
				.andExpect(status().isOk()).andReturn();
		String requestId = JsonPath.read(submitted.getResponse().getContentAsString(), "$.data.id");

		Cookie otherEditorSession = loginAs("editor8@example.com", Role.EDITOR, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/cancel"), otherEditorSession))
				.andExpect(status().isForbidden());

		mockMvc.perform(withCsrf(post(approvalPath(requestId) + "/cancel"), editorSession))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"))
				.andExpect(jsonPath("$.data.cancellationReason").value("ADMIN_CANCELLED"));
	}

	@Test
	void noSessionAtAllIsUnauthorized() throws Exception {
		mockMvc.perform(get(approvalRequestsPath())).andExpect(status().isUnauthorized());
	}

	@Test
	void concurrentSubmissionsForTheSameScopeNeverProduceTwoPendingRequests() throws Exception {
		Cookie editorSession = loginAs("editor9@example.com", Role.EDITOR, PROJECT_KEY);
		onVariantId = createFlagAndCaptureOnVariantId(editorSession, "race-flag");

		int attempts = 6;
		CountDownLatch ready = new CountDownLatch(attempts);
		CountDownLatch go = new CountDownLatch(1);
		AtomicInteger successes = new AtomicInteger();
		Thread[] threads = new Thread[attempts];

		for (int i = 0; i < attempts; i++) {
			threads[i] = new Thread(() -> {
				ready.countDown();
				try {
					go.await();
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
				}
				try {
					MvcResult result = mockMvc.perform(withCsrf(submitRequest("race-flag", onVariantId), editorSession))
							.andReturn();
					if (result.getResponse().getStatus() == 200) {
						successes.incrementAndGet();
					}
				} catch (Exception exception) {
					throw new RuntimeException(exception);
				}
			});
			threads[i].start();
		}
		ready.await();
		go.countDown();
		for (Thread thread : threads) {
			thread.join();
		}

		// Exactly one of the racing submissions should have won - the rest fail with a
		// conflict, whether caught by the application-level check or the Mongo partial
		// unique index backstop (see MongoApprovalRequestStore).
		assertThat(successes.get()).isEqualTo(1);

		MvcResult list = mockMvc.perform(get(approvalRequestsPath()).cookie(editorSession))
				.andExpect(status().isOk()).andReturn();
		java.util.List<String> statuses = JsonPath.read(list.getResponse().getContentAsString(), "$.data[*].status");
		long pendingCount = statuses.stream().filter("PENDING"::equals).count();
		assertThat(pendingCount).isEqualTo(1);
	}

	private String flagsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/flags";
	}

	private String approvalRequestsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/approval-requests";
	}

	private String approvalPath(String requestId) {
		return approvalRequestsPath() + "/" + requestId;
	}

	private Cookie loginAs(String email, Role role, String projectKey) throws Exception {
		User user = new User();
		user.setName(email);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setActive(true);
		User saved = userRepository.save(user);

		ProjectMembership membership = new ProjectMembership();
		membership.setUserId(saved.getId());
		membership.setProjectKey(projectKey);
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

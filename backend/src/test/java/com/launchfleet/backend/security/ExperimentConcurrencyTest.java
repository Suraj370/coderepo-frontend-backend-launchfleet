package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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

import com.launchfleet.backend.environments.Environment;
import com.launchfleet.backend.environments.EnvironmentRepository;
import com.launchfleet.backend.experiments.domain.Experiment;
import com.launchfleet.backend.experiments.ports.ExperimentAssignmentStore;
import com.launchfleet.backend.experiments.ports.ExperimentConflictException;
import com.launchfleet.backend.experiments.ports.ExperimentEventStore;
import com.launchfleet.backend.experiments.ports.ExperimentStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectMembership;
import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.sdk.SdkCredential;
import com.launchfleet.backend.sdk.SdkCredentialRepository;
import com.launchfleet.backend.sdk.SdkCredentialService;
import com.launchfleet.backend.users.Role;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserRepository;

import jakarta.servlet.http.Cookie;

/**
 * Real MongoDB concurrency coverage for Phase 7 - deliberately NOT using mocks for
 * the properties that only a real unique index / real optimistic write can prove
 * (locked architecture instruction: "do not fake MongoDB uniqueness/concurrency
 * semantics with only mocks"). Same latch-coordination style as
 * ApprovalRequestLifecycleConcurrencyTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExperimentConcurrencyTest {

	private static final String PROJECT_KEY = "experiment-concurrency-test";

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
	private ExperimentStore experimentStore;

	@Autowired
	private ExperimentAssignmentStore experimentAssignmentStore;

	@Autowired
	private ExperimentEventStore experimentEventStore;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private SdkCredentialService sdkCredentialService;

	@Autowired
	private SdkCredentialRepository sdkCredentialRepository;

	@BeforeEach
	void setUp() {
		experimentEventStore.deleteAll();
		experimentAssignmentStore.deleteAll();
		experimentStore.deleteAll();
		featureFlagConfigStore.deleteAll();
		featureFlagStore.deleteAll();
		environmentRepository.deleteAll();
		projectRepository.deleteAll();
		sdkCredentialRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();

		Project project = new Project();
		project.setKey(PROJECT_KEY);
		project.setName(PROJECT_KEY);
		Project saved = projectRepository.save(project);

		Environment environment = new Environment();
		environment.setProjectId(saved.getId());
		environment.setKey(ENVIRONMENT_KEY);
		environment.setName("Production");
		environmentRepository.save(environment);
	}

	@Test
	void concurrentFirstAssignmentAttemptsForTheSameUserResultInExactlyOneAuthoritativeAssignment() throws Exception {
		Cookie editorSession = loginAs("editor@example.com", Role.EDITOR);
		MvcResult flagResult = mockMvc
				.perform(withCsrf(post(flagsPath()), editorSession).contentType(MediaType.APPLICATION_JSON).content("""
						{"key":"race-flag","name":"Race Flag","type":"MULTIVARIANT","variants":[
							{"key":"a","name":"A","value":"a"},{"key":"b","name":"B","value":"b"}]}"""))
				.andExpect(status().isOk()).andReturn();
		String body = flagResult.getResponse().getContentAsString();
		String variantA = JsonPath.read(body, "$.data.variants[0].id");
		String variantB = JsonPath.read(body, "$.data.variants[1].id");

		mockMvc.perform(withCsrf(post(experimentsPath()), editorSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"environmentKey":"production","flagKey":"race-flag","key":"race-exp","name":"Race"}"""))
				.andExpect(status().isOk());
		mockMvc.perform(withCsrf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.patch(experimentsPath() + "/race-exp"), editorSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Race","allocation":[{"variantId":"%s","percentage":5000},{"variantId":"%s","percentage":5000}],"conversionEventName":"x"}"""
						.formatted(variantA, variantB)))
				.andExpect(status().isOk());
		mockMvc.perform(withCsrf(post(experimentsPath() + "/race-exp/start"), editorSession))
				.andExpect(status().isOk());
		mockMvc.perform(withCsrf(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
				.patch(flagsPath() + "/race-flag/environments/" + ENVIRONMENT_KEY), editorSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"enabled":true}""")).andExpect(status().isOk());

		SdkCredential credential = new SdkCredential();
		credential.setProjectKey(PROJECT_KEY);
		credential.setEnvironmentKey(ENVIRONMENT_KEY);
		String serverKey = sdkCredentialService.issueServerKey(credential);

		int attempts = 8;
		CountDownLatch ready = new CountDownLatch(attempts);
		CountDownLatch go = new CountDownLatch(1);
		AtomicInteger failures = new AtomicInteger();
		String[] resolvedVariants = new String[attempts];
		Thread[] threads = new Thread[attempts];

		for (int i = 0; i < attempts; i++) {
			int index = i;
			threads[i] = new Thread(() -> {
				ready.countDown();
				try {
					go.await();
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					return;
				}
				try {
					MvcResult result = mockMvc
							.perform(post("/api/v1/sdk/experiments/race-exp/assignments")
									.header("Authorization", "Bearer " + serverKey)
									.contentType(MediaType.APPLICATION_JSON).content("""
											{"userKey":"contested-user"}"""))
							.andReturn();
					if (result.getResponse().getStatus() == 200) {
						resolvedVariants[index] = JsonPath.read(result.getResponse().getContentAsString(),
								"$.data.variantId");
					} else {
						failures.incrementAndGet();
					}
				} catch (Exception exception) {
					failures.incrementAndGet();
				}
			});
			threads[i].start();
		}
		ready.await();
		go.countDown();
		for (Thread thread : threads) {
			thread.join();
		}

		assertThat(failures.get()).as("every concurrent assignment attempt should succeed (get-or-create, never an error)")
				.isZero();

		// Every thread must have observed the SAME variant - the defining proof of
		// "exactly one authoritative assignment", not merely "no exception was thrown".
		Set<String> distinctVariantsObserved = List.of(resolvedVariants).stream().collect(Collectors.toSet());
		assertThat(distinctVariantsObserved).as("all racing requests must resolve to the one authoritative assignment")
				.hasSize(1);

		// And the real MongoDB collection itself holds exactly one document for this
		// (experiment, userKey) pair - not merely "the store's read method returns one",
		// which the unique index (not application logic) is what actually guarantees.
		long assignmentCount = experimentAssignmentStore
				.countByExperimentId(experimentStore.findByProjectIdAndKey(
						projectRepository.findByKey(PROJECT_KEY).orElseThrow().getId(), "race-exp").orElseThrow().getId());
		assertThat(assignmentCount).isEqualTo(1);
	}

	@Test
	void staleExperimentUpdateIsRejectedByRealMongoOptimisticVersionCheck() throws Exception {
		Cookie editorSession = loginAs("editor2@example.com", Role.EDITOR);
		mockMvc.perform(withCsrf(post(flagsPath()), editorSession).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"stale-flag","name":"Stale Flag","type":"BOOLEAN"}""")).andExpect(status().isOk());
		mockMvc.perform(withCsrf(post(experimentsPath()), editorSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"environmentKey":"production","flagKey":"stale-flag","key":"stale-exp","name":"Stale"}"""))
				.andExpect(status().isOk());

		String projectId = projectRepository.findByKey(PROJECT_KEY).orElseThrow().getId();
		Experiment loadedTwice1 = experimentStore.findByProjectIdAndKey(projectId, "stale-exp").orElseThrow();
		Experiment loadedTwice2 = experimentStore.findByProjectIdAndKey(projectId, "stale-exp").orElseThrow();

		loadedTwice1.cancel("actor-1");
		experimentStore.save(loadedTwice1);

		loadedTwice2.cancel("actor-2");
		assertThatThrownBy(() -> experimentStore.save(loadedTwice2)).isInstanceOf(ExperimentConflictException.class);
	}

	private String flagsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/flags";
	}

	private String experimentsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/experiments";
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

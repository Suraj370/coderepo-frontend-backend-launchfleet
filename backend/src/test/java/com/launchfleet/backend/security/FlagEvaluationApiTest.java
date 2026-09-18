package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.launchfleet.backend.environments.Environment;
import com.launchfleet.backend.environments.EnvironmentRepository;
import com.launchfleet.backend.environments.EnvironmentStatus;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FlagType;
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

/** Integration coverage for flag-evaluation recording (SDK) and its dashboard summary. */
@SpringBootTest
@AutoConfigureMockMvc
class FlagEvaluationApiTest {

	private static final String PROJECT_KEY = "flag-eval-test";

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
	private FeatureFlagStore featureFlagStore;

	@Autowired
	private FeatureFlagConfigStore featureFlagConfigStore;

	@Autowired
	private EnvironmentRepository environmentRepository;

	@Autowired
	private SdkCredentialRepository sdkCredentialRepository;

	@Autowired
	private SdkCredentialService sdkCredentialService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	private FeatureFlag flag;

	@BeforeEach
	void setUp() {
		featureFlagConfigStore.deleteAll();
		featureFlagStore.deleteAll();
		environmentRepository.deleteAll();
		sdkCredentialRepository.deleteAll();
		projectRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();

		Project project = new Project();
		project.setKey(PROJECT_KEY);
		project.setName(PROJECT_KEY);
		Project savedProject = projectRepository.save(project);

		Environment environment = new Environment();
		environment.setProjectId(savedProject.getId());
		environment.setKey(ENVIRONMENT_KEY);
		environment.setName(ENVIRONMENT_KEY);
		environment.setStatus(EnvironmentStatus.ACTIVE);
		environmentRepository.save(environment);

		flag = FeatureFlag.create(savedProject.getId(), "checkout-flow", "Checkout Flow", null, FlagType.BOOLEAN,
				null, "seed");
		flag = featureFlagStore.save(flag);
	}

	@Test
	void recordingAnEvaluationIncreasesTheDashboardSummaryCount() throws Exception {
		String serverKey = issueServerKey();
		String variantId = flag.getVariants().get(0).id();

		mockMvc.perform(post(evaluationsPath()).header("Authorization", "Bearer " + serverKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"userKey\":\"user-1\",\"variantId\":\"" + variantId + "\"}"))
				.andExpect(status().isOk());

		Cookie viewer = loginAsMember("viewer@example.com", Role.VIEWER);
		mockMvc.perform(get(summaryPath()).cookie(viewer)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalLast7Days").value(1))
				.andExpect(jsonPath("$.data.byDay.length()").value(7));
	}

	@Test
	void blankUserKeyIsRejected() throws Exception {
		String serverKey = issueServerKey();
		String variantId = flag.getVariants().get(0).id();

		mockMvc.perform(post(evaluationsPath()).header("Authorization", "Bearer " + serverKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"userKey\":\"\",\"variantId\":\"" + variantId + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unknownFlagKeyIs404() throws Exception {
		String serverKey = issueServerKey();

		mockMvc.perform(post("/api/v1/sdk/flags/does-not-exist/evaluations")
				.header("Authorization", "Bearer " + serverKey).contentType(MediaType.APPLICATION_JSON)
				.content("{\"userKey\":\"user-1\",\"variantId\":\"some-variant\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	void summaryWithNoEvaluationsIsZero() throws Exception {
		Cookie viewer = loginAsMember("viewer2@example.com", Role.VIEWER);

		mockMvc.perform(get(summaryPath()).cookie(viewer)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalLast7Days").value(0))
				.andExpect(jsonPath("$.data.percentChangeVsPriorPeriod").value(0.0));
	}

	@Test
	void noSessionAtAllIsUnauthorizedForSummary() throws Exception {
		mockMvc.perform(get(summaryPath())).andExpect(status().isUnauthorized());
	}

	private String evaluationsPath() {
		return "/api/v1/sdk/flags/" + flag.getKey() + "/evaluations";
	}

	private String summaryPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/flags/evaluations/summary";
	}

	private String issueServerKey() {
		SdkCredential credential = new SdkCredential();
		credential.setProjectKey(PROJECT_KEY);
		credential.setEnvironmentKey(ENVIRONMENT_KEY);
		credential.setLabel("test key");

		return sdkCredentialService.issueServerKey(credential);
	}

	private Cookie loginAsMember(String email, Role role) throws Exception {
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
				.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).cookie(csrf)
						.header("X-XSRF-TOKEN", csrf[0].getValue())
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
}

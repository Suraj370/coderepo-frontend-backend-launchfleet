package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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
 * The core invariant of this increment, exercised end to end over real HTTP against
 * real MongoDB: every ACTIVE FeatureFlag has exactly one FeatureFlagConfig per
 * Environment in its project, kept true from both directions (flag created after
 * environments exist; environment created after flags exist), without duplicates,
 * and without leaking across projects.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnvironmentSynchronizationTest {

	private static final String PROJECT_KEY = "sync-test";

	private static final String OTHER_PROJECT_KEY = "sync-test-other";

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
	private PasswordEncoder passwordEncoder;

	private Cookie adminSession;

	@BeforeEach
	void setUp() throws Exception {
		featureFlagConfigStore.deleteAll();
		featureFlagStore.deleteAll();
		environmentRepository.deleteAll();
		projectRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();

		seedProject(PROJECT_KEY);
		seedProject(OTHER_PROJECT_KEY);
		adminSession = loginAs("admin@example.com", Role.ADMIN, PROJECT_KEY);
	}

	@Test
	void creatingAFlagGetsAConfigForEveryEnvironmentThatAlreadyExists() throws Exception {
		createEnvironment("development");
		createEnvironment("staging");

		mockMvc.perform(withCsrf(post(flagsPath()), adminSession).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"two-envs","name":"Two Envs","type":"BOOLEAN"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments.length()").value(2));
	}

	@Test
	void creatingAnEnvironmentBackfillsConfigsForExistingActiveFlags() throws Exception {
		createEnvironment("development");
		mockMvc.perform(withCsrf(post(flagsPath()), adminSession).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"backfill-me","name":"Backfill Me","type":"BOOLEAN"}""")).andExpect(status().isOk());

		createEnvironment("staging");

		mockMvc.perform(get(flagsPath() + "/backfill-me").cookie(adminSession)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments.length()").value(2))
				.andExpect(jsonPath("$.data.environments[?(@.environmentKey == 'staging')].enabled").value(false));
	}

	@Test
	void retiredFlagsAreNotBackfilledWhenANewEnvironmentIsCreated() throws Exception {
		createEnvironment("development");
		mockMvc.perform(withCsrf(post(flagsPath()), adminSession).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"retired-flag","name":"Retired Flag","type":"BOOLEAN"}""")).andExpect(status().isOk());
		mockMvc.perform(withCsrf(post(flagsPath() + "/retired-flag/retire"), adminSession))
				.andExpect(status().isOk());

		createEnvironment("staging");

		mockMvc.perform(get(flagsPath() + "/retired-flag").cookie(adminSession)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments.length()").value(1));
	}

	@Test
	void creatingAnEnvironmentInOneProjectDoesNotAffectAnotherProjectsFlags() throws Exception {
		createEnvironment("development");
		mockMvc.perform(withCsrf(post(flagsPath()), adminSession).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"isolated","name":"Isolated","type":"BOOLEAN"}""")).andExpect(status().isOk());

		Cookie otherSession = loginAs("otheradmin@example.com", Role.ADMIN, OTHER_PROJECT_KEY);
		mockMvc.perform(withCsrf(post(environmentsPath(OTHER_PROJECT_KEY)), otherSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"key":"staging","name":"Staging"}""")).andExpect(status().isOk());

		// The other project's new environment must not have produced a config for
		// PROJECT_KEY's flag - only PROJECT_KEY's own "development" config exists.
		mockMvc.perform(get(flagsPath() + "/isolated").cookie(adminSession)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments.length()").value(1));
	}

	@Test
	void noDuplicateConfigIsCreatedWhenEnvironmentsAndFlagsAlreadyOverlap() throws Exception {
		createEnvironment("development");
		mockMvc.perform(withCsrf(post(flagsPath()), adminSession).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"dup-check","name":"Dup Check","type":"BOOLEAN"}""")).andExpect(status().isOk());

		// This flag already has a "development" config; nothing here should touch it.
		assertThat(featureFlagConfigStore.findByProjectId(resolveProjectId(PROJECT_KEY))).hasSize(1);
	}

	@Test
	void changingOneEnvironmentsConfigDoesNotChangeAnother() throws Exception {
		createEnvironment("development");
		createEnvironment("staging");
		mockMvc.perform(withCsrf(post(flagsPath()), adminSession).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"scoped","name":"Scoped","type":"BOOLEAN"}""")).andExpect(status().isOk());

		mockMvc.perform(withCsrf(patch(flagsPath() + "/scoped/environments/development"), adminSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"enabled":true}""")).andExpect(status().isOk());

		mockMvc.perform(get(flagsPath() + "/scoped").cookie(adminSession)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments[?(@.environmentKey == 'development')].enabled").value(true))
				.andExpect(jsonPath("$.data.environments[?(@.environmentKey == 'staging')].enabled").value(false));
	}

	private void createEnvironment(String key) throws Exception {
		mockMvc.perform(withCsrf(post(environmentsPath(PROJECT_KEY)), adminSession)
				.contentType(MediaType.APPLICATION_JSON).content("{\"key\":\"" + key + "\",\"name\":\"" + key + "\"}"))
				.andExpect(status().isOk());
	}

	private String flagsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/flags";
	}

	private String environmentsPath(String projectKey) {
		return "/api/v1/projects/" + projectKey + "/environments";
	}

	private String resolveProjectId(String projectKey) {
		return projectRepository.findByKey(projectKey).orElseThrow().getId();
	}

	private void seedProject(String projectKey) {
		Project project = new Project();
		project.setKey(projectKey);
		project.setName(projectKey);
		projectRepository.save(project);
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

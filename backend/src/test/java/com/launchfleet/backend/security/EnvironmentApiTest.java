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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

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

/** Integration coverage for environment lifecycle: create, list, project ownership/isolation, key uniqueness, RBAC. */
@SpringBootTest
@AutoConfigureMockMvc
class EnvironmentApiTest {

	private static final String PROJECT_KEY = "env-api-test";

	private static final String OTHER_PROJECT_KEY = "env-api-other";

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

	@BeforeEach
	void setUp() {
		featureFlagConfigStore.deleteAll();
		featureFlagStore.deleteAll();
		environmentRepository.deleteAll();
		projectRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();

		seedProject(PROJECT_KEY);
		seedProject(OTHER_PROJECT_KEY);
	}

	@Test
	void editorCanCreateAndListEnvironments() throws Exception {
		Cookie session = loginAs("editor@example.com", Role.EDITOR, PROJECT_KEY);

		mockMvc.perform(withCsrf(post(environmentsPath(PROJECT_KEY)), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"development","name":"Development"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.key").value("development"))
				.andExpect(jsonPath("$.data.projectId").exists());

		mockMvc.perform(get(environmentsPath(PROJECT_KEY)).cookie(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].key").value("development"));
	}

	@Test
	void duplicateEnvironmentKeyWithinAProjectIsRejected() throws Exception {
		Cookie session = loginAs("editor2@example.com", Role.EDITOR, PROJECT_KEY);

		mockMvc.perform(withCsrf(post(environmentsPath(PROJECT_KEY)), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"staging","name":"Staging"}""")).andExpect(status().isOk());

		mockMvc.perform(withCsrf(post(environmentsPath(PROJECT_KEY)), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"staging","name":"Staging Again"}""")).andExpect(status().isConflict());
	}

	@Test
	void theSameKeyIsAllowedInDifferentProjects() throws Exception {
		Cookie session = loginAs("editor3@example.com", Role.ADMIN, PROJECT_KEY);
		Cookie otherSession = loginAs("editor3-other@example.com", Role.ADMIN, OTHER_PROJECT_KEY);

		mockMvc.perform(withCsrf(post(environmentsPath(PROJECT_KEY)), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"staging","name":"Staging"}""")).andExpect(status().isOk());
		mockMvc.perform(
				withCsrf(post(environmentsPath(OTHER_PROJECT_KEY)), otherSession).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"key":"staging","name":"Staging"}""")).andExpect(status().isOk());
	}

	@Test
	void environmentsCreatedInOneProjectAreNotListedInAnother() throws Exception {
		Cookie session = loginAs("owner@example.com", Role.EDITOR, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(environmentsPath(PROJECT_KEY)), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"staging","name":"Staging"}""")).andExpect(status().isOk());

		Cookie otherSession = loginAs("otherowner@example.com", Role.VIEWER, OTHER_PROJECT_KEY);
		mockMvc.perform(get(environmentsPath(OTHER_PROJECT_KEY)).cookie(otherSession)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(0));
	}

	@Test
	void viewerCanListButNotCreate() throws Exception {
		Cookie session = loginAs("viewer@example.com", Role.VIEWER, PROJECT_KEY);

		mockMvc.perform(get(environmentsPath(PROJECT_KEY)).cookie(session)).andExpect(status().isOk());
		mockMvc.perform(withCsrf(post(environmentsPath(PROJECT_KEY)), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"staging","name":"Staging"}""")).andExpect(status().isForbidden());
	}

	@Test
	void noSessionAtAllIsUnauthorized() throws Exception {
		mockMvc.perform(get(environmentsPath(PROJECT_KEY))).andExpect(status().isUnauthorized());
	}

	@Test
	void missingRequiredFieldIsRejected() throws Exception {
		Cookie session = loginAs("editor4@example.com", Role.EDITOR, PROJECT_KEY);

		mockMvc.perform(withCsrf(post(environmentsPath(PROJECT_KEY)), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"staging"}""")).andExpect(status().isBadRequest());
	}

	private String environmentsPath(String projectKey) {
		return "/api/v1/projects/" + projectKey + "/environments";
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

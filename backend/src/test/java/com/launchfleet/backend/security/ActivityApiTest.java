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

import com.launchfleet.backend.activity.ActivityLogRepository;
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

/** Integration coverage for the activity feed: recorded on flag create/environment create, RBAC. */
@SpringBootTest
@AutoConfigureMockMvc
class ActivityApiTest {

	private static final String PROJECT_KEY = "activity-test";

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
	private ActivityLogRepository activityLogRepository;

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
		activityLogRepository.deleteAll();
		projectRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();

		Project project = new Project();
		project.setKey(PROJECT_KEY);
		project.setName(PROJECT_KEY);
		projectRepository.save(project);
	}

	@Test
	void creatingAFlagAppearsInTheActivityFeed() throws Exception {
		Cookie editor = loginAsMember("editor@example.com", Role.EDITOR);

		mockMvc.perform(withCsrf(post(flagsPath()), editor).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"new-checkout","name":"New Checkout","type":"BOOLEAN"}"""))
				.andExpect(status().isOk());

		mockMvc.perform(get(activityPath()).cookie(editor)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].action").value("FLAG_CREATED"))
				.andExpect(jsonPath("$.data[0].subjectKey").value("new-checkout"))
				.andExpect(jsonPath("$.data[0].actorName").value("editor@example.com"));
	}

	@Test
	void creatingAnEnvironmentAppearsInTheActivityFeedViaTheListener() throws Exception {
		Cookie editor = loginAsMember("editor2@example.com", Role.EDITOR);

		mockMvc.perform(withCsrf(post(environmentsPath()), editor).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"staging","name":"Staging"}""")).andExpect(status().isOk());

		mockMvc.perform(get(activityPath()).cookie(editor)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].action").value("ENVIRONMENT_CREATED"))
				.andExpect(jsonPath("$.data[0].subjectKey").value("staging"));
	}

	@Test
	void viewerCanListActivity() throws Exception {
		Cookie viewer = loginAsMember("viewer@example.com", Role.VIEWER);

		mockMvc.perform(get(activityPath()).cookie(viewer)).andExpect(status().isOk());
	}

	@Test
	void noSessionAtAllIsUnauthorized() throws Exception {
		mockMvc.perform(get(activityPath())).andExpect(status().isUnauthorized());
	}

	private String activityPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/activity";
	}

	private String flagsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/flags";
	}

	private String environmentsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/environments";
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

	private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder, Cookie session)
			throws Exception {
		Cookie[] csrf = obtainCsrfCookie();

		return builder.cookie(session, csrf[0]).header("X-XSRF-TOKEN", csrf[0].getValue());
	}
}

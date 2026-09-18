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

import com.launchfleet.backend.environments.Environment;
import com.launchfleet.backend.environments.EnvironmentRepository;
import com.launchfleet.backend.featureflags.ports.SegmentStore;
import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectMembership;
import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.users.Role;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserRepository;

import jakarta.servlet.http.Cookie;

/**
 * Integration coverage for the Segment management API: CRUD/lifecycle through real
 * HTTP, RBAC (VIEWER/EDITOR), and project isolation. Same style as
 * FeatureFlagApiTest - real MockMvc requests against the real dashboard
 * SecurityFilterChain and a real (Dockerized) MongoDB, not mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SegmentApiTest {

	private static final String PROJECT_KEY = "segment-api-test";

	private static final String OTHER_PROJECT_KEY = "segment-api-other";

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
	private SegmentStore segmentStore;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		segmentStore.deleteAll();
		environmentRepository.deleteAll();
		projectRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();

		seedProject(PROJECT_KEY);
		seedProject(OTHER_PROJECT_KEY);
	}

	@Test
	void editorCanCreateListRetrieveUpdateAndRetireASegment() throws Exception {
		Cookie session = loginAs("editor@example.com", Role.EDITOR, PROJECT_KEY);

		mockMvc.perform(withCsrf(post(segmentsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"beta-users","name":"Beta Users","conditions":[
					{"type":"ATTRIBUTE","attribute":"plan","operator":"EQUALS","values":["gold"]}
				]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.key").value("beta-users"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"));

		mockMvc.perform(get(segmentsPath()).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1));

		mockMvc.perform(get(segmentsPath() + "/beta-users").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Beta Users"));

		mockMvc.perform(withCsrf(patch(segmentsPath() + "/beta-users"), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Renamed","conditions":[
							{"type":"USER_KEY","operator":"IN","values":["alice","bob"]}
						]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Renamed"));

		mockMvc.perform(withCsrf(post(segmentsPath() + "/beta-users/retire"), session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("RETIRED"));

		mockMvc.perform(withCsrf(post(segmentsPath() + "/beta-users/retire"), session))
				.andExpect(status().isConflict());
	}

	@Test
	void viewerCanReadButNotMutate() throws Exception {
		Cookie editorSession = loginAs("editor2@example.com", Role.EDITOR, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(segmentsPath()), editorSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"read-only","name":"Read Only","conditions":[
							{"type":"ATTRIBUTE","attribute":"plan","operator":"EQUALS","values":["gold"]}
						]}""")).andExpect(status().isOk());

		Cookie viewerSession = loginAs("viewer@example.com", Role.VIEWER, PROJECT_KEY);

		mockMvc.perform(get(segmentsPath()).cookie(viewerSession)).andExpect(status().isOk());
		mockMvc.perform(get(segmentsPath() + "/read-only").cookie(viewerSession)).andExpect(status().isOk());

		mockMvc.perform(withCsrf(post(segmentsPath()), viewerSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"blocked","name":"Blocked","conditions":[
							{"type":"ATTRIBUTE","attribute":"plan","operator":"EQUALS","values":["gold"]}
						]}""")).andExpect(status().isForbidden());
		mockMvc.perform(withCsrf(post(segmentsPath() + "/read-only/retire"), viewerSession))
				.andExpect(status().isForbidden());
	}

	@Test
	void aSegmentCreatedInOneProjectIsNotReachableThroughAnotherProjectsUrl() throws Exception {
		Cookie ownerSession = loginAs("owner@example.com", Role.ADMIN, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(segmentsPath()), ownerSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"isolated","name":"Isolated","conditions":[
							{"type":"ATTRIBUTE","attribute":"plan","operator":"EQUALS","values":["gold"]}
						]}""")).andExpect(status().isOk());

		Cookie otherAdminSession = loginAs("otheradmin@example.com", Role.ADMIN, OTHER_PROJECT_KEY);
		mockMvc.perform(get("/api/v1/projects/" + OTHER_PROJECT_KEY + "/segments/isolated").cookie(otherAdminSession))
				.andExpect(status().isNotFound());
	}

	@Test
	void duplicateSegmentKeyWithinAProjectIsRejected() throws Exception {
		Cookie session = loginAs("editor3@example.com", Role.EDITOR, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(segmentsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"dup","name":"First","conditions":[
					{"type":"ATTRIBUTE","attribute":"plan","operator":"EQUALS","values":["gold"]}
				]}""")).andExpect(status().isOk());

		mockMvc.perform(withCsrf(post(segmentsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"dup","name":"Second","conditions":[
					{"type":"ATTRIBUTE","attribute":"plan","operator":"EQUALS","values":["gold"]}
				]}""")).andExpect(status().isConflict());
	}

	@Test
	void segmentWithNoConditionsIsRejected() throws Exception {
		Cookie session = loginAs("editor4@example.com", Role.EDITOR, PROJECT_KEY);

		mockMvc.perform(withCsrf(post(segmentsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"empty","name":"Empty","conditions":[]}""")).andExpect(status().isBadRequest());
	}

	@Test
	void segmentContainingASegmentMatchConditionIsRejected() throws Exception {
		Cookie session = loginAs("editor5@example.com", Role.EDITOR, PROJECT_KEY);

		mockMvc.perform(withCsrf(post(segmentsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"cyclic","name":"Cyclic","conditions":[
					{"type":"SEGMENT_MATCH","operator":"EQUALS","values":["some-id"]}
				]}""")).andExpect(status().isBadRequest());
	}

	private String segmentsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/segments";
	}

	private void seedProject(String projectKey) {
		Project project = new Project();
		project.setKey(projectKey);
		project.setName(projectKey);
		Project saved = projectRepository.save(project);

		Environment environment = new Environment();
		environment.setProjectId(saved.getId());
		environment.setKey(ENVIRONMENT_KEY);
		environment.setName("Production");
		environmentRepository.save(environment);
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

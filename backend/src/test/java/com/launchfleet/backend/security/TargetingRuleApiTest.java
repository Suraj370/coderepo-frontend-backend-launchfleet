package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
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
 * Integration coverage for the targeting-rule API endpoints nested under a flag's
 * environment config: add/update/remove through real HTTP, RBAC, environment-
 * specificity, project isolation, and invalid variant/segment reference handling.
 * Same style as FeatureFlagApiTest/SegmentApiTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TargetingRuleApiTest {

	private static final String PROJECT_KEY = "targeting-api-test";

	private static final String OTHER_PROJECT_KEY = "targeting-api-other";

	private static final String PRODUCTION = "production";

	private static final String STAGING = "staging";

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
	private SegmentStore segmentStore;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		featureFlagConfigStore.deleteAll();
		featureFlagStore.deleteAll();
		segmentStore.deleteAll();
		environmentRepository.deleteAll();
		projectRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();

		seedProject(PROJECT_KEY);
		seedProject(OTHER_PROJECT_KEY);
	}

	@Test
	void editorCanAddUpdateAndRemoveATargetingRule() throws Exception {
		Cookie session = loginAs("editor@example.com", Role.EDITOR, PROJECT_KEY);
		String trueVariantId = createBooleanFlag(session, "checkout");

		MvcResult added = mockMvc
				.perform(withCsrf(post(targetingPath("checkout", PRODUCTION)), session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"priority":1,"conditions":[
									{"type":"USER_KEY","operator":"EQUALS","values":["alice"]}
								],"variantId":"%s"}""".formatted(trueVariantId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments[0].targetingRules.length()").value(1))
				.andExpect(jsonPath("$.data.environments[0].version").value(2))
				.andReturn();
		String ruleId = JsonPath.read(added.getResponse().getContentAsString(),
				"$.data.environments[0].targetingRules[0].id");

		mockMvc.perform(withCsrf(patch(targetingPath("checkout", PRODUCTION) + "/" + ruleId), session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"priority":5,"conditions":[
							{"type":"USER_KEY","operator":"EQUALS","values":["bob"]}
						],"variantId":"%s"}""".formatted(trueVariantId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments[0].targetingRules[0].priority").value(5))
				.andExpect(jsonPath("$.data.environments[0].version").value(3));

		mockMvc.perform(withCsrf(delete(targetingPath("checkout", PRODUCTION) + "/" + ruleId), session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments[0].targetingRules.length()").value(0));
	}

	@Test
	void targetingRulesAreScopedToOneEnvironment() throws Exception {
		Cookie session = loginAs("editor2@example.com", Role.EDITOR, PROJECT_KEY);
		String trueVariantId = createBooleanFlag(session, "env-scoped");

		mockMvc.perform(withCsrf(post(targetingPath("env-scoped", PRODUCTION)), session)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"priority":1,"conditions":[
							{"type":"USER_KEY","operator":"EQUALS","values":["alice"]}
						],"variantId":"%s"}""".formatted(trueVariantId)))
				.andExpect(status().isOk());

		MvcResult flag = mockMvc.perform(get("/api/v1/projects/" + PROJECT_KEY + "/flags/env-scoped").cookie(session))
				.andReturn();
		String body = flag.getResponse().getContentAsString();

		java.util.List<Object> productionRules = JsonPath.read(body,
				"$.data.environments[?(@.environmentKey=='production')].targetingRules");
		java.util.List<Object> stagingRules = JsonPath.read(body,
				"$.data.environments[?(@.environmentKey=='staging')].targetingRules");

		assertThat(((java.util.List<?>) productionRules.get(0))).hasSize(1);
		assertThat(((java.util.List<?>) stagingRules.get(0))).isEmpty();
	}

	@Test
	void addingARuleWithAnInvalidVariantIsRejected() throws Exception {
		Cookie session = loginAs("editor3@example.com", Role.EDITOR, PROJECT_KEY);
		createBooleanFlag(session, "bad-variant");

		mockMvc.perform(withCsrf(post(targetingPath("bad-variant", PRODUCTION)), session)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"priority":1,"conditions":[
							{"type":"USER_KEY","operator":"EQUALS","values":["alice"]}
						],"variantId":"not-a-real-variant"}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void addingARuleReferencingAnUnknownSegmentIsRejected() throws Exception {
		Cookie session = loginAs("editor4@example.com", Role.EDITOR, PROJECT_KEY);
		String trueVariantId = createBooleanFlag(session, "bad-segment");

		mockMvc.perform(withCsrf(post(targetingPath("bad-segment", PRODUCTION)), session)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"priority":1,"conditions":[
							{"type":"SEGMENT_MATCH","operator":"EQUALS","values":["no-such-segment"]}
						],"variantId":"%s"}""".formatted(trueVariantId)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void addingARuleWithZeroConditionsIsRejected() throws Exception {
		Cookie session = loginAs("editor5@example.com", Role.EDITOR, PROJECT_KEY);
		String trueVariantId = createBooleanFlag(session, "no-conditions");

		mockMvc.perform(withCsrf(post(targetingPath("no-conditions", PRODUCTION)), session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"priority":1,"conditions":[],"variantId":"%s"}""".formatted(trueVariantId)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void viewerCanReadButNotMutateTargetingRules() throws Exception {
		Cookie editorSession = loginAs("editor6@example.com", Role.EDITOR, PROJECT_KEY);
		String trueVariantId = createBooleanFlag(editorSession, "viewer-check");

		Cookie viewerSession = loginAs("viewer@example.com", Role.VIEWER, PROJECT_KEY);

		mockMvc.perform(withCsrf(post(targetingPath("viewer-check", PRODUCTION)), viewerSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"priority":1,"conditions":[
							{"type":"USER_KEY","operator":"EQUALS","values":["alice"]}
						],"variantId":"%s"}""".formatted(trueVariantId)))
				.andExpect(status().isForbidden());
	}

	@Test
	void targetingRulesOnAFlagInOneProjectAreNotReachableThroughAnotherProjectsUrl() throws Exception {
		Cookie ownerSession = loginAs("owner@example.com", Role.ADMIN, PROJECT_KEY);
		createBooleanFlag(ownerSession, "isolated");

		Cookie otherAdminSession = loginAs("otheradmin@example.com", Role.ADMIN, OTHER_PROJECT_KEY);
		mockMvc.perform(withCsrf(
				post("/api/v1/projects/" + OTHER_PROJECT_KEY + "/flags/isolated/environments/" + PRODUCTION
						+ "/targeting-rules"),
				otherAdminSession).contentType(MediaType.APPLICATION_JSON).content("""
						{"priority":1,"conditions":[
							{"type":"USER_KEY","operator":"EQUALS","values":["alice"]}
						],"variantId":"whatever"}""")).andExpect(status().isNotFound());
	}

	private String createBooleanFlag(Cookie session, String key) throws Exception {
		MvcResult created = mockMvc
				.perform(withCsrf(post("/api/v1/projects/" + PROJECT_KEY + "/flags"), session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"key\":\"" + key + "\",\"name\":\"" + key + "\",\"type\":\"BOOLEAN\"}"))
				.andExpect(status().isOk()).andReturn();

		return JsonPath.read(created.getResponse().getContentAsString(), "$.data.variants[1].id");
	}

	private String targetingPath(String flagKey, String environmentKey) {
		return "/api/v1/projects/" + PROJECT_KEY + "/flags/" + flagKey + "/environments/" + environmentKey
				+ "/targeting-rules";
	}

	private void seedProject(String projectKey) {
		Project project = new Project();
		project.setKey(projectKey);
		project.setName(projectKey);
		Project saved = projectRepository.save(project);

		Environment production = new Environment();
		production.setProjectId(saved.getId());
		production.setKey(PRODUCTION);
		production.setName("Production");
		environmentRepository.save(production);

		Environment staging = new Environment();
		staging.setProjectId(saved.getId());
		staging.setKey(STAGING);
		staging.setName("Staging");
		environmentRepository.save(staging);
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

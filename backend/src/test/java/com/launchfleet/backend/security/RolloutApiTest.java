package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.List;
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

/**
 * Integration coverage for the rollout sub-resource nested under a flag's
 * environment config: PUT/DELETE through real HTTP, RBAC, environment-specificity,
 * project isolation, and invalid-allocation handling. Same style as
 * TargetingRuleApiTest/SegmentApiTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RolloutApiTest {

	private static final String PROJECT_KEY = "rollout-api-test";

	private static final String OTHER_PROJECT_KEY = "rollout-api-other";

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
	void editorCanSetAndClearARollout() throws Exception {
		Cookie session = loginAs("editor@example.com", Role.EDITOR, PROJECT_KEY);
		List<String> variantIds = createBooleanFlag(session, "checkout");

		mockMvc.perform(withCsrf(put(rolloutPath("checkout", PRODUCTION)), session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"allocations":[
							{"variantId":"%s","percentage":4000},
							{"variantId":"%s","percentage":6000}
						]}""".formatted(variantIds.get(0), variantIds.get(1))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments[0].rollout.allocations.length()").value(2))
				.andExpect(jsonPath("$.data.environments[0].version").value(2));

		mockMvc.perform(withCsrf(delete(rolloutPath("checkout", PRODUCTION)), session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments[0].rollout").value(nullValue()))
				.andExpect(jsonPath("$.data.environments[0].version").value(3));
	}

	@Test
	void puttingARolloutReplacesTheCompleteAllocationList() throws Exception {
		Cookie session = loginAs("editor2@example.com", Role.EDITOR, PROJECT_KEY);
		List<String> variantIds = createBooleanFlag(session, "replace");

		mockMvc.perform(withCsrf(put(rolloutPath("replace", PRODUCTION)), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"allocations":[
							{"variantId":"%s","percentage":4000},
							{"variantId":"%s","percentage":6000}
						]}""".formatted(variantIds.get(0), variantIds.get(1))))
				.andExpect(status().isOk());

		mockMvc.perform(withCsrf(put(rolloutPath("replace", PRODUCTION)), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"allocations":[{"variantId":"%s","percentage":10000}]}""".formatted(variantIds.get(1))))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments[0].rollout.allocations.length()").value(1))
				.andExpect(jsonPath("$.data.environments[0].version").value(3));
	}

	@Test
	void getFlagIncludesRolloutInTheEnvironmentConfigResponse() throws Exception {
		Cookie session = loginAs("editor3@example.com", Role.EDITOR, PROJECT_KEY);
		List<String> variantIds = createBooleanFlag(session, "get-includes-rollout");

		mockMvc.perform(withCsrf(put(rolloutPath("get-includes-rollout", PRODUCTION)), session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"allocations":[{"variantId":"%s","percentage":10000}]}""".formatted(variantIds.get(0))))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/projects/" + PROJECT_KEY + "/flags/get-includes-rollout").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments[0].rollout.allocations.length()").value(1));
	}

	@Test
	void invalidAllocationTotalIsRejected() throws Exception {
		Cookie session = loginAs("editor4@example.com", Role.EDITOR, PROJECT_KEY);
		List<String> variantIds = createBooleanFlag(session, "bad-total");

		mockMvc.perform(withCsrf(put(rolloutPath("bad-total", PRODUCTION)), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"allocations":[
							{"variantId":"%s","percentage":4000},
							{"variantId":"%s","percentage":4000}
						]}""".formatted(variantIds.get(0), variantIds.get(1))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void percentageOutOfRangeIsRejected() throws Exception {
		Cookie session = loginAs("editor5@example.com", Role.EDITOR, PROJECT_KEY);
		List<String> variantIds = createBooleanFlag(session, "out-of-range");

		mockMvc.perform(withCsrf(put(rolloutPath("out-of-range", PRODUCTION)), session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"allocations":[{"variantId":"%s","percentage":-1}]}""".formatted(variantIds.get(0))))
				.andExpect(status().isBadRequest());
	}

	@Test
	void unknownVariantIsRejected() throws Exception {
		Cookie session = loginAs("editor6@example.com", Role.EDITOR, PROJECT_KEY);
		createBooleanFlag(session, "unknown-variant");

		mockMvc.perform(withCsrf(put(rolloutPath("unknown-variant", PRODUCTION)), session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"allocations":[{"variantId":"not-a-real-variant","percentage":10000}]}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rolloutIsScopedToOneEnvironment() throws Exception {
		Cookie session = loginAs("editor7@example.com", Role.EDITOR, PROJECT_KEY);
		List<String> variantIds = createBooleanFlag(session, "env-scoped");

		mockMvc.perform(withCsrf(put(rolloutPath("env-scoped", PRODUCTION)), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"allocations":[{"variantId":"%s","percentage":10000}]}""".formatted(variantIds.get(0))))
				.andExpect(status().isOk());

		MvcResult flag = mockMvc.perform(get("/api/v1/projects/" + PROJECT_KEY + "/flags/env-scoped").cookie(session))
				.andReturn();
		String body = flag.getResponse().getContentAsString();

		List<Object> productionRollout = JsonPath.read(body,
				"$.data.environments[?(@.environmentKey=='production')].rollout");
		List<Object> stagingRollout = JsonPath.read(body,
				"$.data.environments[?(@.environmentKey=='staging')].rollout");

		assertThat(productionRollout.get(0)).isNotNull();
		assertThat(stagingRollout.get(0)).isNull();
	}

	@Test
	void viewerCanReadButNotModifyRollout() throws Exception {
		Cookie editorSession = loginAs("editor8@example.com", Role.EDITOR, PROJECT_KEY);
		List<String> variantIds = createBooleanFlag(editorSession, "viewer-check");

		Cookie viewerSession = loginAs("viewer@example.com", Role.VIEWER, PROJECT_KEY);

		mockMvc.perform(get("/api/v1/projects/" + PROJECT_KEY + "/flags/viewer-check").cookie(viewerSession))
				.andExpect(status().isOk());

		mockMvc.perform(withCsrf(put(rolloutPath("viewer-check", PRODUCTION)), viewerSession)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"allocations":[{"variantId":"%s","percentage":10000}]}""".formatted(variantIds.get(0))))
				.andExpect(status().isForbidden());

		mockMvc.perform(withCsrf(delete(rolloutPath("viewer-check", PRODUCTION)), viewerSession))
				.andExpect(status().isForbidden());
	}

	@Test
	void rolloutOnAFlagInOneProjectIsNotReachableThroughAnotherProjectsUrl() throws Exception {
		Cookie ownerSession = loginAs("owner@example.com", Role.ADMIN, PROJECT_KEY);
		createBooleanFlag(ownerSession, "isolated");

		Cookie otherAdminSession = loginAs("otheradmin@example.com", Role.ADMIN, OTHER_PROJECT_KEY);
		mockMvc.perform(withCsrf(
				put("/api/v1/projects/" + OTHER_PROJECT_KEY + "/flags/isolated/environments/" + PRODUCTION
						+ "/rollout"),
				otherAdminSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"allocations":[{"variantId":"whatever","percentage":10000}]}"""))
				.andExpect(status().isNotFound());
	}

	private List<String> createBooleanFlag(Cookie session, String key) throws Exception {
		MvcResult created = mockMvc
				.perform(withCsrf(post("/api/v1/projects/" + PROJECT_KEY + "/flags"), session)
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"key\":\"" + key + "\",\"name\":\"" + key + "\",\"type\":\"BOOLEAN\"}"))
				.andExpect(status().isOk()).andReturn();

		String body = created.getResponse().getContentAsString();
		String falseVariantId = JsonPath.read(body, "$.data.variants[0].id");
		String trueVariantId = JsonPath.read(body, "$.data.variants[1].id");

		return List.of(falseVariantId, trueVariantId);
	}

	private String rolloutPath(String flagKey, String environmentKey) {
		return "/api/v1/projects/" + PROJECT_KEY + "/flags/" + flagKey + "/environments/" + environmentKey
				+ "/rollout";
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

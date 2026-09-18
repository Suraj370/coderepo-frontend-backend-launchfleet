package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Integration coverage for the FeatureFlag management API: CRUD through real HTTP,
 * RBAC (VIEWER/EDITOR), project isolation, environment ownership, and validation.
 * Same style as SecurityBoundaryTest - real MockMvc requests against the real
 * dashboard SecurityFilterChain and a real (Dockerized) MongoDB, not mocks.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FeatureFlagApiTest {

	private static final String PROJECT_KEY = "flag-api-test";

	private static final String OTHER_PROJECT_KEY = "flag-api-other";

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
	private PasswordEncoder passwordEncoder;

	@Autowired
	private SdkCredentialService sdkCredentialService;

	@Autowired
	private SdkCredentialRepository sdkCredentialRepository;

	@BeforeEach
	void setUp() {
		featureFlagConfigStore.deleteAll();
		featureFlagStore.deleteAll();
		environmentRepository.deleteAll();
		projectRepository.deleteAll();
		sdkCredentialRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();

		seedProject(PROJECT_KEY);
		seedProject(OTHER_PROJECT_KEY);
	}

	@Test
	void editorCanCreateListRetrieveUpdateEnableAndRetireAFlag() throws Exception {
		Cookie session = loginAs("editor@example.com", Role.EDITOR, PROJECT_KEY);

		mockMvc.perform(withCsrf(post(flagsPath()), session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"new-checkout","name":"New Checkout","description":"d","type":"BOOLEAN"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.key").value("new-checkout"))
				.andExpect(jsonPath("$.data.status").value("ACTIVE"))
				.andExpect(jsonPath("$.data.variants.length()").value(2))
				.andExpect(jsonPath("$.data.environments.length()").value(1))
				.andExpect(jsonPath("$.data.environments[0].enabled").value(false));

		mockMvc.perform(get(flagsPath()).cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1));

		mockMvc.perform(get(flagsPath() + "/new-checkout").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("New Checkout"));

		mockMvc.perform(withCsrf(patch(flagsPath() + "/new-checkout"), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Renamed Checkout","description":"updated"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("Renamed Checkout"));

		mockMvc.perform(withCsrf(patch(flagsPath() + "/new-checkout/environments/" + ENVIRONMENT_KEY), session)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"enabled":true}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments[0].enabled").value(true))
				.andExpect(jsonPath("$.data.environments[0].version").value(2));

		mockMvc.perform(withCsrf(post(flagsPath() + "/new-checkout/retire"), session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("RETIRED"));

		mockMvc.perform(withCsrf(post(flagsPath() + "/new-checkout/retire"), session))
				.andExpect(status().isConflict());
	}

	@Test
	void multivariantFlagCanBeCreatedWithExplicitVariants() throws Exception {
		Cookie session = loginAs("editor2@example.com", Role.EDITOR, PROJECT_KEY);

		mockMvc.perform(withCsrf(post(flagsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"theme","name":"Theme","type":"MULTIVARIANT","variants":[
					{"key":"red","name":"Red","value":"#f00"},
					{"key":"blue","name":"Blue","value":"#00f"}
				]}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.variants.length()").value(2));
	}

	@Test
	void defaultVariantCanBeChangedForASpecificEnvironmentOnly() throws Exception {
		Cookie session = loginAs("editor2b@example.com", Role.EDITOR, PROJECT_KEY);

		MvcResult created = mockMvc
				.perform(withCsrf(post(flagsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
						{"key":"variant-swap","name":"Variant Swap","type":"BOOLEAN"}"""))
				.andExpect(status().isOk()).andReturn();
		String trueVariantId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.variants[1].id");

		mockMvc.perform(withCsrf(patch(flagsPath() + "/variant-swap/environments/" + ENVIRONMENT_KEY), session)
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"defaultVariantId\":\"" + trueVariantId + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.environments[0].defaultVariantId").value(trueVariantId))
				.andExpect(jsonPath("$.data.environments[0].version").value(2));
	}

	@Test
	void changingTheDefaultVariantToOneThatDoesNotBelongToTheFlagIsRejected() throws Exception {
		Cookie session = loginAs("editor2c@example.com", Role.EDITOR, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(flagsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"variant-check","name":"Variant Check","type":"BOOLEAN"}""")).andExpect(status().isOk());

		mockMvc.perform(withCsrf(patch(flagsPath() + "/variant-check/environments/" + ENVIRONMENT_KEY), session)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"defaultVariantId":"not-a-real-variant"}"""))
				.andExpect(status().isBadRequest());
	}

	@Test
	void viewerCanReadButNotMutate() throws Exception {
		Cookie editorSession = loginAs("editor3@example.com", Role.EDITOR, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(flagsPath()), editorSession).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"read-only-target","name":"Target","type":"BOOLEAN"}""")).andExpect(status().isOk());

		Cookie viewerSession = loginAs("viewer@example.com", Role.VIEWER, PROJECT_KEY);

		mockMvc.perform(get(flagsPath()).cookie(viewerSession)).andExpect(status().isOk());
		mockMvc.perform(get(flagsPath() + "/read-only-target").cookie(viewerSession)).andExpect(status().isOk());

		mockMvc.perform(withCsrf(post(flagsPath()), viewerSession).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"blocked","name":"Blocked","type":"BOOLEAN"}""")).andExpect(status().isForbidden());
		mockMvc.perform(withCsrf(patch(flagsPath() + "/read-only-target"), viewerSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"name":"Nope"}""")).andExpect(status().isForbidden());
		mockMvc.perform(withCsrf(post(flagsPath() + "/read-only-target/retire"), viewerSession))
				.andExpect(status().isForbidden());
	}

	@Test
	void aFlagCreatedInOneProjectIsNotReachableThroughAnotherProjectsUrl() throws Exception {
		Cookie ownerSession = loginAs("owner@example.com", Role.ADMIN, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(flagsPath()), ownerSession).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"isolated","name":"Isolated","type":"BOOLEAN"}""")).andExpect(status().isOk());

		// Same user, ADMIN in the *other* project too - but the flag simply doesn't
		// exist there, regardless of role: project isolation, not just RBAC.
		Cookie otherAdminSession = loginAs("otheradmin@example.com", Role.ADMIN, OTHER_PROJECT_KEY);
		mockMvc.perform(get("/api/v1/projects/" + OTHER_PROJECT_KEY + "/flags/isolated").cookie(otherAdminSession))
				.andExpect(status().isNotFound());
	}

	@Test
	void enablingAnUnknownEnvironmentIsRejected() throws Exception {
		Cookie session = loginAs("editor4@example.com", Role.EDITOR, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(flagsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"env-check","name":"Env Check","type":"BOOLEAN"}""")).andExpect(status().isOk());

		mockMvc.perform(withCsrf(patch(flagsPath() + "/env-check/environments/does-not-exist"), session)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"enabled":true}""")).andExpect(status().isNotFound());
	}

	@Test
	void duplicateFlagKeyWithinAProjectIsRejected() throws Exception {
		Cookie session = loginAs("editor5@example.com", Role.EDITOR, PROJECT_KEY);
		mockMvc.perform(withCsrf(post(flagsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"dup","name":"First","type":"BOOLEAN"}""")).andExpect(status().isOk());

		mockMvc.perform(withCsrf(post(flagsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"dup","name":"Second","type":"BOOLEAN"}""")).andExpect(status().isConflict());
	}

	@Test
	void invalidFlagTypeIsRejected() throws Exception {
		Cookie session = loginAs("editor6@example.com", Role.EDITOR, PROJECT_KEY);

		mockMvc.perform(withCsrf(post(flagsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"bad-type","name":"Bad","type":"NOT_A_REAL_TYPE"}""")).andExpect(status().isBadRequest());
	}

	@Test
	void missingRequiredFieldIsRejected() throws Exception {
		Cookie session = loginAs("editor7@example.com", Role.EDITOR, PROJECT_KEY);

		mockMvc.perform(withCsrf(post(flagsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
				{"key":"no-name","type":"BOOLEAN"}""")).andExpect(status().isBadRequest());
	}

	@Test
	void sdkCredentialCannotReachManagementEndpoints() throws Exception {
		SdkCredential credential = new SdkCredential();
		credential.setProjectKey(PROJECT_KEY);
		credential.setEnvironmentKey(ENVIRONMENT_KEY);
		String serverKey = sdkCredentialService.issueServerKey(credential);

		// The dashboard chain never reads Authorization headers at all - an SDK
		// credential here is simply unauthenticated, never authorized-but-forbidden.
		mockMvc.perform(get(flagsPath()).header("Authorization", "Bearer " + serverKey))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void noSessionAtAllIsUnauthorized() throws Exception {
		mockMvc.perform(get(flagsPath())).andExpect(status().isUnauthorized());
	}

	private String flagsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/flags";
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

	/** Attaches a fresh, matching CSRF cookie+header pair to a state-changing request. */
	private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder builder, Cookie session)
			throws Exception {
		Cookie[] csrf = obtainCsrfCookie();

		return builder.cookie(session, csrf[0]).header("X-XSRF-TOKEN", csrf[0].getValue());
	}
}

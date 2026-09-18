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
import com.launchfleet.backend.experiments.ports.ExperimentAssignmentStore;
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
 * Integration coverage for the Phase 7 experiment API: RBAC, full DRAFT -> RUNNING
 * -> COMPLETED lifecycle through real HTTP, project isolation, retirement cascade,
 * and SDK_SERVER-authenticated assignment/event ingestion. Same style as
 * FeatureFlagApiTest/ApprovalRequestApiTest - real MockMvc requests against the
 * real dashboard and SDK SecurityFilterChains and a real (Dockerized) MongoDB.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ExperimentApiTest {

	private static final String PROJECT_KEY = "experiment-api-test";

	private static final String OTHER_PROJECT_KEY = "experiment-api-other";

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

		seedProject(PROJECT_KEY);
		seedProject(OTHER_PROJECT_KEY);
	}

	private String createFlagAndCaptureVariantIds(Cookie session, String flagKey, String[] variantIdsOut)
			throws Exception {
		MvcResult created = mockMvc
				.perform(withCsrf(post(flagsPath()), session).contentType(MediaType.APPLICATION_JSON).content("""
						{"key":"%s","name":"%s","type":"MULTIVARIANT","variants":[
							{"key":"a","name":"A","value":"a"},
							{"key":"b","name":"B","value":"b"}
						]}""".formatted(flagKey, flagKey)))
				.andExpect(status().isOk()).andReturn();
		String body = created.getResponse().getContentAsString();
		variantIdsOut[0] = JsonPath.read(body, "$.data.variants[0].id");
		variantIdsOut[1] = JsonPath.read(body, "$.data.variants[1].id");

		return JsonPath.read(body, "$.data.id");
	}

	@Test
	void editorCanCreateConfigureStartAndStopAnExperiment() throws Exception {
		Cookie editorSession = loginAs("editor@example.com", Role.EDITOR, PROJECT_KEY);
		String[] variantIds = new String[2];
		createFlagAndCaptureVariantIds(editorSession, "checkout", variantIds);

		MvcResult created = mockMvc
				.perform(withCsrf(post(experimentsPath()), editorSession).contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"environmentKey":"production","flagKey":"checkout","key":"copy-test","name":"Copy Test","description":"d"}"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("DRAFT")).andReturn();
		String experimentKey = "copy-test";
		assertThat((String) JsonPath.read(created.getResponse().getContentAsString(), "$.data.key"))
				.isEqualTo(experimentKey);

		mockMvc.perform(withCsrf(patch(experimentPath(experimentKey)), editorSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"name":"Copy Test","description":"d","allocation":[
							{"variantId":"%s","percentage":5000},
							{"variantId":"%s","percentage":5000}
						],"conversionEventName":"purchase_completed"}""".formatted(variantIds[0], variantIds[1])))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.conversionEventName").value("purchase_completed"));

		mockMvc.perform(withCsrf(post(experimentPath(experimentKey) + "/start"), editorSession))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("RUNNING"));

		mockMvc.perform(withCsrf(post(experimentPath(experimentKey) + "/stop"), editorSession))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("COMPLETED"));
	}

	@Test
	void invalidAllocationIsRejected() throws Exception {
		Cookie editorSession = loginAs("editor2@example.com", Role.EDITOR, PROJECT_KEY);
		String[] variantIds = new String[2];
		createFlagAndCaptureVariantIds(editorSession, "bad-alloc-flag", variantIds);
		mockMvc.perform(withCsrf(post(experimentsPath()), editorSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"environmentKey":"production","flagKey":"bad-alloc-flag","key":"bad-alloc","name":"Bad"}"""))
				.andExpect(status().isOk());

		mockMvc.perform(withCsrf(patch(experimentPath("bad-alloc")), editorSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"name":"Bad","allocation":[{"variantId":"%s","percentage":4000}],"conversionEventName":"x"}"""
						.formatted(variantIds[0])))
				.andExpect(status().isBadRequest());
	}

	@Test
	void viewerCanReadButNotMutate() throws Exception {
		Cookie editorSession = loginAs("editor3@example.com", Role.EDITOR, PROJECT_KEY);
		String[] variantIds = new String[2];
		createFlagAndCaptureVariantIds(editorSession, "viewer-flag", variantIds);
		mockMvc.perform(withCsrf(post(experimentsPath()), editorSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"environmentKey":"production","flagKey":"viewer-flag","key":"viewer-exp","name":"V"}"""))
				.andExpect(status().isOk());

		Cookie viewerSession = loginAs("viewer@example.com", Role.VIEWER, PROJECT_KEY);
		mockMvc.perform(get(experimentsPath()).cookie(viewerSession)).andExpect(status().isOk());
		mockMvc.perform(get(experimentPath("viewer-exp")).cookie(viewerSession)).andExpect(status().isOk());
		mockMvc.perform(get(experimentPath("viewer-exp") + "/metrics").cookie(viewerSession))
				.andExpect(status().isOk());

		mockMvc.perform(withCsrf(post(experimentsPath()), viewerSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"environmentKey":"production","flagKey":"viewer-flag","key":"blocked","name":"Blocked"}"""))
				.andExpect(status().isForbidden());
		mockMvc.perform(withCsrf(post(experimentPath("viewer-exp") + "/start"), viewerSession))
				.andExpect(status().isForbidden());
	}

	@Test
	void anExperimentCreatedInOneProjectIsNotReachableThroughAnotherProjectsUrl() throws Exception {
		Cookie ownerSession = loginAs("owner@example.com", Role.ADMIN, PROJECT_KEY);
		String[] variantIds = new String[2];
		createFlagAndCaptureVariantIds(ownerSession, "isolated-flag", variantIds);
		mockMvc.perform(withCsrf(post(experimentsPath()), ownerSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"environmentKey":"production","flagKey":"isolated-flag","key":"isolated","name":"Isolated"}"""))
				.andExpect(status().isOk());

		Cookie otherAdminSession = loginAs("otheradmin@example.com", Role.ADMIN, OTHER_PROJECT_KEY);
		mockMvc.perform(
				get("/api/v1/projects/" + OTHER_PROJECT_KEY + "/experiments/isolated").cookie(otherAdminSession))
				.andExpect(status().isNotFound());
	}

	@Test
	void flagRetirementCancelsTheExperiment() throws Exception {
		Cookie editorSession = loginAs("editor4@example.com", Role.EDITOR, PROJECT_KEY);
		String[] variantIds = new String[2];
		createFlagAndCaptureVariantIds(editorSession, "retire-flag", variantIds);
		mockMvc.perform(withCsrf(post(experimentsPath()), editorSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"environmentKey":"production","flagKey":"retire-flag","key":"retire-exp","name":"R"}"""))
				.andExpect(status().isOk());

		mockMvc.perform(withCsrf(post(flagsPath() + "/retire-flag/retire"), editorSession))
				.andExpect(status().isOk());

		mockMvc.perform(get(experimentPath("retire-exp")).cookie(editorSession)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("CANCELLED"));
	}

	@Test
	void sdkCredentialCanEstablishAnAssignmentAndRecordAConversionEvent() throws Exception {
		Cookie editorSession = loginAs("editor5@example.com", Role.EDITOR, PROJECT_KEY);
		String[] variantIds = new String[2];
		createFlagAndCaptureVariantIds(editorSession, "sdk-flag", variantIds);
		mockMvc.perform(withCsrf(post(experimentsPath()), editorSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"environmentKey":"production","flagKey":"sdk-flag","key":"sdk-exp","name":"S"}"""))
				.andExpect(status().isOk());
		mockMvc.perform(withCsrf(patch(experimentPath("sdk-exp")), editorSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"S","allocation":[{"variantId":"%s","percentage":5000},{"variantId":"%s","percentage":5000}],"conversionEventName":"purchase_completed"}"""
						.formatted(variantIds[0], variantIds[1])))
				.andExpect(status().isOk());
		mockMvc.perform(withCsrf(post(experimentPath("sdk-exp") + "/start"), editorSession)).andExpect(status().isOk());
		// CreateExperimentAssignment requires the flag to be currently enabled in this
		// environment (see its Javadoc) - CreateFeatureFlag auto-creates a disabled config.
		mockMvc.perform(withCsrf(patch(flagsPath() + "/sdk-flag/environments/" + ENVIRONMENT_KEY), editorSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"enabled":true}""")).andExpect(status().isOk());

		SdkCredential credential = new SdkCredential();
		credential.setProjectKey(PROJECT_KEY);
		credential.setEnvironmentKey(ENVIRONMENT_KEY);
		String serverKey = sdkCredentialService.issueServerKey(credential);

		MvcResult assignmentResult = mockMvc
				.perform(post("/api/v1/sdk/experiments/sdk-exp/assignments").header("Authorization", "Bearer " + serverKey)
						.contentType(MediaType.APPLICATION_JSON).content("""
								{"userKey":"end-user-1"}"""))
				.andExpect(status().isOk()).andReturn();
		String variantId = JsonPath.read(assignmentResult.getResponse().getContentAsString(), "$.data.variantId");

		mockMvc.perform(post("/api/v1/sdk/experiments/sdk-exp/events").header("Authorization", "Bearer " + serverKey)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"userKey":"end-user-1","eventName":"purchase_completed"}"""))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.variantId").value(variantId));

		mockMvc.perform(get(experimentPath("sdk-exp") + "/metrics").cookie(editorSession)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2));
	}

	@Test
	void eventWithoutAPriorAssignmentIsRejected() throws Exception {
		Cookie editorSession = loginAs("editor6@example.com", Role.EDITOR, PROJECT_KEY);
		String[] variantIds = new String[2];
		createFlagAndCaptureVariantIds(editorSession, "no-assign-flag", variantIds);
		mockMvc.perform(withCsrf(post(experimentsPath()), editorSession).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"environmentKey":"production","flagKey":"no-assign-flag","key":"no-assign-exp","name":"N"}"""))
				.andExpect(status().isOk());
		mockMvc.perform(withCsrf(patch(experimentPath("no-assign-exp")), editorSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"name":"N","allocation":[{"variantId":"%s","percentage":10000}],"conversionEventName":"x"}"""
						.formatted(variantIds[0])))
				.andExpect(status().isOk());
		mockMvc.perform(withCsrf(post(experimentPath("no-assign-exp") + "/start"), editorSession))
				.andExpect(status().isOk());

		SdkCredential credential = new SdkCredential();
		credential.setProjectKey(PROJECT_KEY);
		credential.setEnvironmentKey(ENVIRONMENT_KEY);
		String serverKey = sdkCredentialService.issueServerKey(credential);

		mockMvc.perform(post("/api/v1/sdk/experiments/no-assign-exp/events").header("Authorization", "Bearer " + serverKey)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"userKey":"never-assigned","eventName":"x"}"""))
				.andExpect(status().isNotFound());
	}

	@Test
	void dashboardSessionCannotReachSdkExperimentEndpoints() throws Exception {
		Cookie editorSession = loginAs("editor7@example.com", Role.EDITOR, PROJECT_KEY);

		mockMvc.perform(withCsrf(post("/api/v1/sdk/experiments/anything/assignments"), editorSession)
				.contentType(MediaType.APPLICATION_JSON).content("""
						{"userKey":"x"}""")).andExpect(status().isUnauthorized());
	}

	private String flagsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/flags";
	}

	private String experimentsPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/experiments";
	}

	private String experimentPath(String experimentKey) {
		return experimentsPath() + "/" + experimentKey;
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

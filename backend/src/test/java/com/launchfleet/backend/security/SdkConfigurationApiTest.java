package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.launchfleet.backend.environments.Environment;
import com.launchfleet.backend.environments.EnvironmentRepository;
import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.ConditionOperator;
import com.launchfleet.backend.featureflags.domain.ConditionType;
import com.launchfleet.backend.featureflags.domain.FeatureFlag;
import com.launchfleet.backend.featureflags.domain.FeatureFlagConfig;
import com.launchfleet.backend.featureflags.domain.FlagType;
import com.launchfleet.backend.featureflags.domain.Rollout;
import com.launchfleet.backend.featureflags.domain.Allocation;
import com.launchfleet.backend.featureflags.domain.Segment;
import com.launchfleet.backend.featureflags.domain.TargetingRule;
import com.launchfleet.backend.featureflags.domain.Variant;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.featureflags.ports.SegmentStore;
import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.sdk.SdkCredential;
import com.launchfleet.backend.sdk.SdkCredentialRepository;
import com.launchfleet.backend.sdk.SdkCredentialService;

/**
 * Integration coverage for the SDK configuration distribution endpoint
 * (GET /api/v1/sdk/config): scope-from-credential-only, project/environment isolation,
 * credential type/lifecycle enforcement (all reusing the existing sdkFilterChain/
 * SdkCredentialService unchanged), payload shape (flags/segments/rollout), and
 * conditional GET (ETag/If-None-Match) against the read-time aggregate version.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SdkConfigurationApiTest {

	private static final String PRODUCTION = "production";

	private static final String STAGING = "staging";

	@Autowired
	private MockMvc mockMvc;

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
	private SdkCredentialRepository sdkCredentialRepository;

	@Autowired
	private SdkCredentialService sdkCredentialService;

	private String projectId;

	@BeforeEach
	void setUp() {
		sdkCredentialRepository.deleteAll();
		featureFlagConfigStore.deleteAll();
		featureFlagStore.deleteAll();
		segmentStore.deleteAll();
		environmentRepository.deleteAll();
		projectRepository.deleteAll();

		Project project = new Project();
		project.setKey("sdk-config-test");
		project.setName("Sdk Config Test");
		projectId = projectRepository.save(project).getId();

		saveEnvironment(PRODUCTION);
		saveEnvironment(STAGING);
	}

	@Test
	void serverCredentialReceivesTheFullEvaluableConfigurationForItsOwnEnvironment() throws Exception {
		Segment segment = segmentStore.save(Segment.create(projectId, "beta-users", "Beta Users",
				List.of(new Condition(ConditionType.USER_KEY, null, ConditionOperator.IN, List.of("u1", "u2"))),
				"tester"));

		FeatureFlag flag = featureFlagStore.save(FeatureFlag.create(projectId, "checkout", "Checkout", "d",
				FlagType.BOOLEAN, null, "tester"));
		Variant onVariant = flag.getVariants().get(1);
		Variant offVariant = flag.getVariants().get(0);

		FeatureFlagConfig config = FeatureFlagConfig.createDisabled(flag.getId(), environmentId(PRODUCTION), projectId,
				offVariant.id(), "tester");
		config.updateEnabled(true, "tester");
		TargetingRule rule = TargetingRule.create(0,
				List.of(new Condition(ConditionType.SEGMENT_MATCH, null, ConditionOperator.EQUALS,
						List.of(segment.getId()))),
				onVariant.id());
		config.updateTargetingRules(List.of(rule), "tester");
		config.updateRollout(new Rollout(List.of(new Allocation(onVariant.id(), 4000),
				new Allocation(offVariant.id(), 6000))), "tester");
		featureFlagConfigStore.save(config);

		String serverKey = issueServerKey(PRODUCTION);

		mockMvc.perform(sdkConfig(serverKey)).andExpect(status().isOk())
				.andExpect(jsonPath("$.environmentKey").value(PRODUCTION))
				.andExpect(jsonPath("$.flags.length()").value(1))
				.andExpect(jsonPath("$.flags[0].key").value("checkout"))
				.andExpect(jsonPath("$.flags[0].enabled").value(true))
				.andExpect(jsonPath("$.flags[0].defaultVariant.key").value("false"))
				.andExpect(jsonPath("$.flags[0].targetingRules.length()").value(1))
				.andExpect(jsonPath("$.flags[0].targetingRules[0].conditions[0].type").value("SEGMENT_MATCH"))
				.andExpect(jsonPath("$.flags[0].rollout.allocations.length()").value(2))
				.andExpect(jsonPath("$.segments.length()").value(1))
				.andExpect(jsonPath("$.segments[0].key").value("beta-users"))
				.andExpect(jsonPath("$.version").isNotEmpty());
	}

	@Test
	void unreferencedSegmentsAreNotIncludedInThePayload() throws Exception {
		segmentStore.save(Segment.create(projectId, "unused", "Unused",
				List.of(new Condition(ConditionType.USER_KEY, null, ConditionOperator.IN, List.of("u1"))), "tester"));

		String serverKey = issueServerKey(PRODUCTION);

		mockMvc.perform(sdkConfig(serverKey)).andExpect(status().isOk())
				.andExpect(jsonPath("$.segments.length()").value(0));
	}

	@Test
	void environmentIsolationOnlyTheCredentialsOwnEnvironmentConfigIsReturned() throws Exception {
		FeatureFlag flag = featureFlagStore.save(
				FeatureFlag.create(projectId, "env-scoped", "Env Scoped", "d", FlagType.BOOLEAN, null, "tester"));
		FeatureFlagConfig prodConfig = FeatureFlagConfig.createDisabled(flag.getId(), environmentId(PRODUCTION),
				projectId, flag.getVariants().get(0).id(), "tester");
		prodConfig.updateEnabled(true, "tester");
		featureFlagConfigStore.save(prodConfig);
		featureFlagConfigStore
				.save(FeatureFlagConfig.createDisabled(flag.getId(), environmentId(STAGING), projectId,
						flag.getVariants().get(0).id(), "tester"));

		String stagingKey = issueServerKey(STAGING);

		mockMvc.perform(sdkConfig(stagingKey)).andExpect(status().isOk())
				.andExpect(jsonPath("$.environmentKey").value(STAGING))
				.andExpect(jsonPath("$.flags[0].enabled").value(false));
	}

	@Test
	void projectIsolationACredentialNeverSeesAnotherProjectsFlags() throws Exception {
		Project otherProject = new Project();
		otherProject.setKey("sdk-config-other");
		otherProject.setName("Other");
		String otherProjectId = projectRepository.save(otherProject).getId();

		Environment otherProduction = new Environment();
		otherProduction.setProjectId(otherProjectId);
		otherProduction.setKey(PRODUCTION);
		otherProduction.setName("Production");
		environmentRepository.save(otherProduction);

		featureFlagStore.save(
				FeatureFlag.create(projectId, "in-first-project", "First", "d", FlagType.BOOLEAN, null, "tester"));

		SdkCredential otherCredential = new SdkCredential();
		otherCredential.setProjectKey("sdk-config-other");
		otherCredential.setEnvironmentKey(PRODUCTION);
		String otherServerKey = sdkCredentialService.issueServerKey(otherCredential);

		mockMvc.perform(sdkConfig(otherServerKey)).andExpect(status().isOk())
				.andExpect(jsonPath("$.flags.length()").value(0));
	}

	@Test
	void expiredCredentialIsRejected() throws Exception {
		SdkCredential credential = newCredential(PRODUCTION);
		credential.setExpiresAt(Instant.now().minusSeconds(60));
		String serverKey = sdkCredentialService.issueServerKey(credential);

		mockMvc.perform(sdkConfig(serverKey)).andExpect(status().isUnauthorized());
	}

	@Test
	void revokedCredentialIsRejected() throws Exception {
		SdkCredential credential = newCredential(PRODUCTION);
		credential.setActive(false);
		String serverKey = sdkCredentialService.issueServerKey(credential);

		mockMvc.perform(sdkConfig(serverKey)).andExpect(status().isUnauthorized());
	}

	@Test
	void clientSideCredentialIsForbiddenFromTheServerOnlyConfigurationEndpoint() throws Exception {
		String clientSideId = sdkCredentialService.issueClientSideId(newCredential(PRODUCTION));

		mockMvc.perform(sdkConfig(clientSideId)).andExpect(status().isForbidden());
	}

	@Test
	void noCredentialAtAllIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/sdk/config")).andExpect(status().isUnauthorized());
	}

	@Test
	void thereIsNoWriteOperationExposedOnTheSdkConfigurationRoute() throws Exception {
		String serverKey = issueServerKey(PRODUCTION);

		// No POST handler is mapped for this route at all (only GET) - Spring resolves
		// that to 404 in this app's configuration, not 405, but either way proves the
		// same thing: there is no reachable write/management operation here.
		mockMvc.perform(post("/api/v1/sdk/config").header(SecurityConstants.AUTHORIZATION_HEADER,
				SecurityConstants.BEARER_PREFIX + serverKey)).andExpect(status().isNotFound());
	}

	@Test
	void unchangedConfigurationReturns304AndChangedConfigurationReturnsANewPayload() throws Exception {
		FeatureFlag flag = featureFlagStore.save(
				FeatureFlag.create(projectId, "etag-check", "Etag Check", "d", FlagType.BOOLEAN, null, "tester"));
		FeatureFlagConfig config = FeatureFlagConfig.createDisabled(flag.getId(), environmentId(PRODUCTION), projectId,
				flag.getVariants().get(0).id(), "tester");
		featureFlagConfigStore.save(config);

		String serverKey = issueServerKey(PRODUCTION);

		MvcResult first = mockMvc.perform(sdkConfig(serverKey)).andExpect(status().isOk()).andReturn();
		String etag = first.getResponse().getHeader("ETag");
		assertThat(etag).isNotBlank();

		mockMvc.perform(sdkConfig(serverKey).header("If-None-Match", etag)).andExpect(status().isNotModified());

		FeatureFlagConfig reloaded = featureFlagConfigStore
				.findByFeatureFlagIdAndEnvironmentId(flag.getId(), environmentId(PRODUCTION)).orElseThrow();
		reloaded.updateEnabled(true, "tester");
		featureFlagConfigStore.save(reloaded);

		MvcResult second = mockMvc.perform(sdkConfig(serverKey).header("If-None-Match", etag))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.flags[0].enabled").value(true)).andReturn();
		String secondEtag = second.getResponse().getHeader("ETag");
		assertThat(secondEtag).isNotEqualTo(etag);
	}

	// --- helpers ---

	private MockHttpServletRequestBuilder sdkConfig(String bearerToken) {
		return get("/api/v1/sdk/config").header(SecurityConstants.AUTHORIZATION_HEADER,
				SecurityConstants.BEARER_PREFIX + bearerToken);
	}

	private String issueServerKey(String environmentKey) {
		return sdkCredentialService.issueServerKey(newCredential(environmentKey));
	}

	private SdkCredential newCredential(String environmentKey) {
		SdkCredential credential = new SdkCredential();
		credential.setProjectKey("sdk-config-test");
		credential.setEnvironmentKey(environmentKey);

		return credential;
	}

	private void saveEnvironment(String key) {
		Environment environment = new Environment();
		environment.setProjectId(projectId);
		environment.setKey(key);
		environment.setName(key);
		environmentRepository.save(environment);
	}

	private String environmentId(String key) {
		return environmentRepository.findByProjectIdAndKey(projectId, key).orElseThrow().getId();
	}
}

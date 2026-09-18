package com.launchfleet.backend.seed;

import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.bson.Document;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.launchfleet.backend.approvals.application.SubmitApprovalRequest;
import com.launchfleet.backend.approvals.ports.ApprovalRequestStore;
import com.launchfleet.backend.environments.Environment;
import com.launchfleet.backend.environments.EnvironmentRepository;
import com.launchfleet.backend.environments.application.ProvisionStandardEnvironments;
import com.launchfleet.backend.experiments.application.CreateExperiment;
import com.launchfleet.backend.experiments.ports.ExperimentAssignmentStore;
import com.launchfleet.backend.experiments.ports.ExperimentStore;
import com.launchfleet.backend.featureflags.application.CreateFeatureFlag;
import com.launchfleet.backend.featureflags.application.CreateSegment;
import com.launchfleet.backend.featureflags.application.FeatureFlagView;
import com.launchfleet.backend.featureflags.domain.Condition;
import com.launchfleet.backend.featureflags.domain.ConditionOperator;
import com.launchfleet.backend.featureflags.domain.ConditionType;
import com.launchfleet.backend.featureflags.domain.FlagType;
import com.launchfleet.backend.featureflags.ports.FeatureFlagConfigStore;
import com.launchfleet.backend.featureflags.ports.FeatureFlagStore;
import com.launchfleet.backend.featureflags.ports.SegmentStore;
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

/**
 * Wipes and reseeds demo data. This is destructive (see clearDatabase) and must never
 * run anywhere but a controlled local/dev context - isDestructiveSeedAllowed() requires
 * TWO independent, explicit signals (the 'dev' profile AND SEED_DATABASE=true) before a
 * Spring context (and therefore a MongoDB connection) is even created, so a single
 * misconfigured flag - or a misconfigured `spring.mongodb.uri` pointed at a shared
 * environment - can't be enough on its own to wipe real data. Delegates to the same
 * Spring-managed repositories/services the running application uses (SdkCredentialService
 * for credential generation/hashing, PasswordEncoder for the demo password) rather than
 * duplicating that logic.
 */
@SpringBootApplication(scanBasePackages = "com.launchfleet.backend")
public class SeedRunner {

	private static final String SEPARATOR = "========================================";

	private static final String REQUIRED_PROFILE = "dev";

	private static final String SEED_DATABASE_ENV = "SEED_DATABASE";

	public static void main(String[] args) {
		requireExplicitSeedOptIn();

		ConfigurableApplicationContext context = new SpringApplicationBuilder(SeedRunner.class)
				.web(WebApplicationType.NONE).logStartupInfo(false).run(args);

		try {
			seed(context);
		} catch (RuntimeException exception) {
			System.err.println();
			System.err.println("Seeding failed: " + exception.getMessage());
			exception.printStackTrace();
			SpringApplication.exit(context, () -> 1);
			System.exit(1);
		}

		System.out.println("Disconnected from MongoDB");
		SpringApplication.exit(context, () -> 0);
		System.exit(0);
	}

	/**
	 * Refuses to proceed - before any Spring context or MongoDB connection exists -
	 * unless BOTH the 'dev' profile and SEED_DATABASE=true were explicitly set. The
	 * `seed` Gradle task sets both for you; running SeedRunner any other way must opt
	 * in the same way. Two independent flags, not one, so a single stray/inherited
	 * environment variable (e.g. a leftover SPRING_PROFILES_ACTIVE=dev on a shared
	 * host) can't by itself trigger a destructive run.
	 */
	private static void requireExplicitSeedOptIn() {
		if (isDestructiveSeedAllowed(activeProfiles(), System.getenv(SEED_DATABASE_ENV))) {
			return;
		}

		System.err.println(SEPARATOR);
		System.err.println("Refusing to seed: this wipes and reseeds the database.");
		System.err.println("Both of the following must be set to confirm this is an intended, local dev run:");
		System.err.println("  SPRING_PROFILES_ACTIVE=" + REQUIRED_PROFILE);
		System.err.println("  " + SEED_DATABASE_ENV + "=true");
		System.err.println("e.g. ./gradlew seed (sets both for you)");
		System.err.println(SEPARATOR);
		System.exit(1);
	}

	static boolean isDestructiveSeedAllowed(String activeProfiles, String seedDatabaseFlag) {
		return hasProfile(activeProfiles) && "true".equalsIgnoreCase(trim(seedDatabaseFlag));
	}

	private static String activeProfiles() {
		String env = System.getenv("SPRING_PROFILES_ACTIVE");

		return (env != null && !env.isBlank()) ? env : System.getProperty("spring.profiles.active");
	}

	private static boolean hasProfile(String profiles) {
		if (profiles == null || profiles.isBlank()) {
			return false;
		}

		for (String profile : profiles.split(",")) {
			if (profile.trim().toLowerCase(Locale.ROOT).equals(REQUIRED_PROFILE)) {
				return true;
			}
		}

		return false;
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

	private static void seed(ConfigurableApplicationContext context) {
		System.out.println(SEPARATOR);
		System.out.println("Database Seeding");
		System.out.println(SEPARATOR);
		System.out.println();
		System.out.println("Connecting to MongoDB...");
		context.getBean(MongoTemplate.class).executeCommand(new Document("ping", 1));
		System.out.println("Connected to MongoDB");

		UserRepository userRepository = context.getBean(UserRepository.class);
		ProjectMembershipRepository projectMembershipRepository = context.getBean(ProjectMembershipRepository.class);
		SdkCredentialRepository sdkCredentialRepository = context.getBean(SdkCredentialRepository.class);
		SdkCredentialService sdkCredentialService = context.getBean(SdkCredentialService.class);
		PasswordEncoder passwordEncoder = context.getBean(PasswordEncoder.class);
		ProjectRepository projectRepository = context.getBean(ProjectRepository.class);
		EnvironmentRepository environmentRepository = context.getBean(EnvironmentRepository.class);
		FeatureFlagStore featureFlagStore = context.getBean(FeatureFlagStore.class);
		FeatureFlagConfigStore featureFlagConfigStore = context.getBean(FeatureFlagConfigStore.class);
		SegmentStore segmentStore = context.getBean(SegmentStore.class);
		ExperimentStore experimentStore = context.getBean(ExperimentStore.class);
		ExperimentAssignmentStore experimentAssignmentStore = context.getBean(ExperimentAssignmentStore.class);
		ApprovalRequestStore approvalRequestStore = context.getBean(ApprovalRequestStore.class);
		ProvisionStandardEnvironments provisionStandardEnvironments = context
				.getBean(ProvisionStandardEnvironments.class);
		CreateFeatureFlag createFeatureFlag = context.getBean(CreateFeatureFlag.class);
		CreateSegment createSegment = context.getBean(CreateSegment.class);
		CreateExperiment createExperiment = context.getBean(CreateExperiment.class);
		SubmitApprovalRequest submitApprovalRequest = context.getBean(SubmitApprovalRequest.class);

		clearDatabase(userRepository, projectMembershipRepository, sdkCredentialRepository, projectRepository,
				environmentRepository, featureFlagStore, featureFlagConfigStore, segmentStore, experimentStore,
				experimentAssignmentStore, approvalRequestStore);

		User user = seedUser(userRepository, passwordEncoder);
		seedMembership(projectMembershipRepository, user);
		seedServerKey(sdkCredentialService);
		seedClientSideId(sdkCredentialService);
		Project project = seedProject(projectRepository);
		provisionStandardEnvironments.execute(project.getId(), user.getId());
		List<Environment> environments = environmentRepository.findByProjectId(project.getId());

		seedFeatureFlagSegmentExperimentAndApproval(createFeatureFlag, createSegment, createExperiment,
				submitApprovalRequest, user);

		report(userRepository, projectMembershipRepository, sdkCredentialRepository, featureFlagStore, segmentStore,
				experimentStore, approvalRequestStore, user, project, environments);
	}

	private static void clearDatabase(UserRepository userRepository,
			ProjectMembershipRepository projectMembershipRepository, SdkCredentialRepository sdkCredentialRepository,
			ProjectRepository projectRepository, EnvironmentRepository environmentRepository,
			FeatureFlagStore featureFlagStore, FeatureFlagConfigStore featureFlagConfigStore,
			SegmentStore segmentStore, ExperimentStore experimentStore,
			ExperimentAssignmentStore experimentAssignmentStore, ApprovalRequestStore approvalRequestStore) {
		System.out.println();
		System.out.println("Clearing existing collections...");

		approvalRequestStore.deleteAll();
		experimentAssignmentStore.deleteAll();
		experimentStore.deleteAll();
		segmentStore.deleteAll();
		featureFlagConfigStore.deleteAll();
		featureFlagStore.deleteAll();
		environmentRepository.deleteAll();
		projectRepository.deleteAll();
		sdkCredentialRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();
	}

	private static User seedUser(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		System.out.println();
		System.out.println("Seeding dashboard user...");

		User user = new User();
		user.setName(SeedData.DEMO_NAME);
		user.setEmail(SeedData.DEMO_EMAIL);
		user.setPasswordHash(passwordEncoder.encode(SeedData.DEMO_PASSWORD));
		user.setActive(true);
		user.setCreatedAt(Instant.now());
		user.setUpdatedAt(Instant.now());

		return userRepository.save(user);
	}

	private static void seedMembership(ProjectMembershipRepository projectMembershipRepository, User user) {
		System.out.println();
		System.out.println("Seeding project membership...");

		ProjectMembership membership = new ProjectMembership();
		membership.setUserId(user.getId());
		membership.setProjectKey(SeedData.DEMO_PROJECT_KEY);
		membership.setRole(Role.ADMIN);
		membership.setCreatedAt(Instant.now());
		projectMembershipRepository.save(membership);
	}

	private static void seedServerKey(SdkCredentialService sdkCredentialService) {
		System.out.println();
		System.out.println("Seeding SDK server key...");

		SdkCredential credential = new SdkCredential();
		credential.setProjectKey(SeedData.DEMO_PROJECT_KEY);
		credential.setEnvironmentKey(SeedData.DEMO_ENVIRONMENT_KEY);
		credential.setLabel("Seeded server key");
		credential.setActive(true);
		credential.setCreatedAt(Instant.now());

		sdkCredentialService.issueServerKey(credential);
	}

	private static void seedClientSideId(SdkCredentialService sdkCredentialService) {
		System.out.println();
		System.out.println("Seeding SDK client-side id...");

		SdkCredential credential = new SdkCredential();
		credential.setProjectKey(SeedData.DEMO_PROJECT_KEY);
		credential.setEnvironmentKey(SeedData.DEMO_ENVIRONMENT_KEY);
		credential.setLabel("Seeded client-side id");
		credential.setActive(true);
		credential.setCreatedAt(Instant.now());

		sdkCredentialService.issueClientSideId(credential);
	}

	/**
	 * Seeds one representative example of each headline capability (flag with
	 * per-environment configs, segment, experiment, and a pending approval request) so
	 * a fresh checkout demonstrates the product instead of starting empty. Reuses the
	 * same application-layer use cases the dashboard itself calls, rather than
	 * constructing domain objects or documents directly, so seeded data is guaranteed
	 * to satisfy the same invariants as anything created through the API.
	 */
	private static void seedFeatureFlagSegmentExperimentAndApproval(CreateFeatureFlag createFeatureFlag,
			CreateSegment createSegment, CreateExperiment createExperiment,
			SubmitApprovalRequest submitApprovalRequest, User user) {
		System.out.println();
		System.out.println("Seeding feature flag...");

		FeatureFlagView flagView = createFeatureFlag.execute(SeedData.DEMO_PROJECT_KEY, "checkout-redesign",
				"Checkout Redesign", "Rolls out the redesigned checkout flow to eligible users.", FlagType.BOOLEAN,
				null, user.getId());

		System.out.println();
		System.out.println("Seeding segment...");

		createSegment.execute(SeedData.DEMO_PROJECT_KEY, "beta-users", "Beta Users",
				List.of(new Condition(ConditionType.ATTRIBUTE, "plan", ConditionOperator.EQUALS, List.of("beta"))),
				user.getId());

		System.out.println();
		System.out.println("Seeding experiment...");

		createExperiment.execute(SeedData.DEMO_PROJECT_KEY, SeedData.DEMO_ENVIRONMENT_KEY, flagView.flag().getKey(),
				"checkout-redesign-impact", "Checkout Redesign Impact",
				"Measures conversion impact of the redesigned checkout flow.", user.getId());

		System.out.println();
		System.out.println("Seeding approval request...");

		String enabledVariantId = flagView.flag().getVariants().get(1).id();
		submitApprovalRequest.execute(SeedData.DEMO_PROJECT_KEY, flagView.flag().getKey(),
				SeedData.DEMO_ENVIRONMENT_KEY, true, enabledVariantId, List.of(), List.of(), user.getId());
	}

	private static Project seedProject(ProjectRepository projectRepository) {
		System.out.println();
		System.out.println("Seeding project...");

		Project project = new Project();
		project.setKey(SeedData.DEMO_PROJECT_KEY);
		project.setName("Default");
		project.setCreatedAt(Instant.now());

		return projectRepository.save(project);
	}

	/**
	 * Never prints the password or either SDK credential's secret value, even though
	 * this is a dev-only, doubly-gated script - only non-secret identifying metadata.
	 * Credentials are write-once by design (see SdkCredentialService); capture them at
	 * creation time in code if a test or tool needs to use one.
	 */
	private static void report(UserRepository userRepository, ProjectMembershipRepository projectMembershipRepository,
			SdkCredentialRepository sdkCredentialRepository, FeatureFlagStore featureFlagStore,
			SegmentStore segmentStore, ExperimentStore experimentStore, ApprovalRequestStore approvalRequestStore,
			User user, Project project, List<Environment> environments) {
		System.out.println();
		System.out.println(SEPARATOR);
		System.out.println("Seeding completed successfully!");
		System.out.println(SEPARATOR);
		System.out.println();
		System.out.println("Collection counts:");
		System.out.println("  Users:                " + userRepository.count());
		System.out.println("  Project memberships:  " + projectMembershipRepository.count());
		System.out.println("  SDK credentials:      " + sdkCredentialRepository.count());
		System.out.println("  Feature flags:        " + featureFlagStore.findByProjectId(project.getId()).size());
		System.out.println("  Segments:             " + segmentStore.findByProjectId(project.getId()).size());
		System.out.println("  Experiments:          " + experimentStore.findByProjectId(project.getId()).size());
		System.out.println(
				"  Approval requests:    " + approvalRequestStore.findByProjectId(project.getId()).size());
		System.out.println();
		System.out.println("Dashboard user created:");
		System.out.println("  Email:   " + user.getEmail());
		System.out.println("  Project: " + SeedData.DEMO_PROJECT_KEY + " (role: ADMIN)");
		System.out.println("  Environments: "
				+ environments.stream().map(Environment::getKey).reduce((a, b) -> a + ", " + b).orElse("(none)"));
		System.out.println();
		System.out.println("SDK credentials created for project '" + SeedData.DEMO_PROJECT_KEY + "', environment '"
				+ SeedData.DEMO_ENVIRONMENT_KEY + "' (server key + client-side id).");
		System.out.println(SEPARATOR);
		System.out.println();
	}
}

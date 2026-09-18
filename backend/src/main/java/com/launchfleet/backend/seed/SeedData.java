package com.launchfleet.backend.seed;

/**
 * Fixed, non-secret demo values - not read from env vars. This mirrors the reference
 * calendar repo's seed convention: a known, printable set of credentials is what makes
 * the README's "seeded access: copyable login credentials" requirement possible.
 */
public final class SeedData {

	public static final String DEMO_NAME = "Ada Admin";

	public static final String DEMO_EMAIL = "admin@launchfleet.dev";

	public static final String DEMO_PASSWORD = "launchfleet-demo";

	public static final String DEMO_PROJECT_KEY = "default";

	public static final String DEMO_ENVIRONMENT_KEY = "production";

	private SeedData() {
	}
}

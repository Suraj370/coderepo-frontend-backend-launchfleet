package com.launchfleet.backend.sdk;

/**
 * The two SDK credential trust tiers, and why they're not interchangeable:
 *
 * SERVER is confidential - stored only as a SHA-256 hash (see SdkCredentialService),
 * never returned after creation, and grants the SDK filter chain's default authority
 * (see SecurityConfig's sdkFilterChain, which requires SDK_SERVER by default). It's
 * meant to live in a backend process, not in anything a browser can read.
 *
 * CLIENT_SIDE is not confidential by design - it's meant to be embedded in public
 * browser JS, so it's stored and looked up in plaintext (hashing a value an attacker
 * can already read verbatim would add nothing). Authenticating with one only proves
 * "which project/environment," not elevated trust: it gets its own SDK_CLIENT_SIDE
 * authority, disjoint from SDK_SERVER, and reaches nothing by default - a future
 * route has to explicitly opt it in once a client-safe operation (evaluation for a
 * single end-user context, never raw flag-rule retrieval) actually exists to protect.
 */
public enum SdkCredentialType {

	SERVER,

	CLIENT_SIDE

}

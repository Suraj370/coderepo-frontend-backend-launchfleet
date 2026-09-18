package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.launchfleet.backend.projects.ProjectMembership;
import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.sdk.SdkCredential;
import com.launchfleet.backend.sdk.SdkCredentialRepository;
import com.launchfleet.backend.sdk.SdkCredentialService;
import com.launchfleet.backend.users.Role;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserRepository;

import jakarta.servlet.http.Cookie;

/**
 * Exercises both SecurityFilterChains end to end against the real seeded pieces
 * (users, project memberships, SDK credentials) rather than mocked Authentication -
 * see the plan's verification section for why this uses a test-only fixture
 * controller (TestFixtureController) instead of real business routes.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityBoundaryTest {

	private static final String PROJECT_KEY = "acme";

	private static final String OTHER_PROJECT_KEY = "globex";

	private static final String PASSWORD = "correct horse battery staple";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProjectMembershipRepository projectMembershipRepository;

	@Autowired
	private SdkCredentialRepository sdkCredentialRepository;

	@Autowired
	private SdkCredentialService sdkCredentialService;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void cleanUp() {
		sdkCredentialRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();
	}

	// --- Dashboard ---

	@Test
	void loginEstablishesASessionAndSessionEndpointReflectsIt() throws Exception {
		seedUser("editor@example.com", Role.EDITOR, true);
		Cookie[] csrf = obtainCsrfCookie();

		MvcResult loginResult = mockMvc
				.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.cookie(csrf).header("X-XSRF-TOKEN", csrf[0].getValue())
						.content(loginJson("editor@example.com", PASSWORD)))
				.andExpect(status().isOk()).andReturn();

		Cookie session = loginResult.getResponse().getCookie(SecurityConstants.SESSION_COOKIE_NAME);
		assertThat(session).isNotNull();

		mockMvc.perform(get("/api/v1/auth/session").cookie(session)).andExpect(status().isOk());
	}

	@Test
	void wrongPasswordIsRejected() throws Exception {
		seedUser("editor@example.com", Role.EDITOR, true);
		Cookie[] csrf = obtainCsrfCookie();

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.cookie(csrf).header("X-XSRF-TOKEN", csrf[0].getValue())
				.content(loginJson("editor@example.com", "not the password"))).andExpect(status().isUnauthorized());
	}

	@Test
	void inactiveUserIsRejected() throws Exception {
		seedUser("disabled@example.com", Role.EDITOR, false);
		Cookie[] csrf = obtainCsrfCookie();

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.cookie(csrf).header("X-XSRF-TOKEN", csrf[0].getValue())
				.content(loginJson("disabled@example.com", PASSWORD))).andExpect(status().isUnauthorized());
	}

	@Test
	void logoutInvalidatesTheSession() throws Exception {
		seedUser("editor@example.com", Role.EDITOR, true);
		Cookie[] csrf = obtainCsrfCookie();
		Cookie session = login("editor@example.com", csrf);

		mockMvc.perform(post("/api/v1/auth/logout").cookie(session, csrf[0])
				.header("X-XSRF-TOKEN", csrf[0].getValue())).andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/auth/session").cookie(session)).andExpect(status().isUnauthorized());
	}

	@Test
	void sessionIdChangesAcrossLogin() throws Exception {
		seedUser("editor@example.com", Role.EDITOR, true);
		Cookie[] csrf = obtainCsrfCookie();
		Cookie firstSession = login("editor@example.com", csrf);

		// Re-authenticate while already carrying a session cookie from the first login:
		// fixation protection means a fresh session id is issued, not the existing one
		// reused - otherwise an attacker who fixed that cookie before login would keep
		// a valid, now-authenticated session id after the victim logs in through it.
		MvcResult secondLogin = mockMvc
				.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.cookie(firstSession, csrf[0]).header("X-XSRF-TOKEN", csrf[0].getValue())
						.content(loginJson("editor@example.com", PASSWORD)))
				.andExpect(status().isOk()).andReturn();

		Cookie secondSession = secondLogin.getResponse().getCookie(SecurityConstants.SESSION_COOKIE_NAME);
		assertThat(secondSession).isNotNull();
		assertThat(secondSession.getValue()).isNotEqualTo(firstSession.getValue());
	}

	@Test
	void postWithoutACsrfTokenIsRejected() throws Exception {
		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.content(loginJson("nobody@example.com", PASSWORD))).andExpect(status().isForbidden());
	}

	@Test
	void postWithAnIncorrectCsrfTokenIsRejected() throws Exception {
		// A real, currently-issued XSRF-TOKEN cookie, but a header value that doesn't
		// match it - proves the double-submit comparison actually checks equality
		// rather than just "some header was present."
		Cookie[] csrf = obtainCsrfCookie();

		mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
				.cookie(csrf).header("X-XSRF-TOKEN", csrf[0].getValue() + "-tampered")
				.content(loginJson("nobody@example.com", PASSWORD))).andExpect(status().isForbidden());
	}

	@Test
	void editorRoleCanReadAndWriteItsProjectButViewerCanOnlyRead() throws Exception {
		seedUser("editor@example.com", Role.EDITOR, true);
		seedUser("viewer@example.com", Role.VIEWER, true);

		Cookie[] editorCsrfForLogin = obtainCsrfCookie();
		Cookie editorSession = login("editor@example.com", editorCsrfForLogin);
		Cookie[] viewerCsrfForLogin = obtainCsrfCookie();
		Cookie viewerSession = login("viewer@example.com", viewerCsrfForLogin);

		mockMvc.perform(get("/api/v1/projects/" + PROJECT_KEY + "/ping").cookie(editorSession))
				.andExpect(status().isOk());

		Cookie[] editorCsrf = obtainCsrfCookie();
		mockMvc.perform(post("/api/v1/projects/" + PROJECT_KEY + "/ping").cookie(editorSession)
				.cookie(editorCsrf).header("X-XSRF-TOKEN", editorCsrf[0].getValue()))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/projects/" + PROJECT_KEY + "/ping").cookie(viewerSession))
				.andExpect(status().isOk());

		Cookie[] viewerCsrf = obtainCsrfCookie();
		mockMvc.perform(post("/api/v1/projects/" + PROJECT_KEY + "/ping").cookie(viewerSession)
				.cookie(viewerCsrf).header("X-XSRF-TOKEN", viewerCsrf[0].getValue()))
				.andExpect(status().isForbidden());
	}

	@Test
	void noSessionAtAllIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/projects/" + PROJECT_KEY + "/ping")).andExpect(status().isUnauthorized());
	}

	@Test
	void projectMembershipDoesNotGrantAccessToADifferentProject() throws Exception {
		// EDITOR in PROJECT_KEY only - no membership row exists for OTHER_PROJECT_KEY at all.
		seedUser("editor@example.com", Role.EDITOR, true);
		Cookie[] loginCsrf = obtainCsrfCookie();
		Cookie session = login("editor@example.com", loginCsrf);

		mockMvc.perform(get("/api/v1/projects/" + PROJECT_KEY + "/ping").cookie(session))
				.andExpect(status().isOk());
		Cookie[] ownProjectCsrf = obtainCsrfCookie();
		mockMvc.perform(post("/api/v1/projects/" + PROJECT_KEY + "/ping").cookie(session)
				.cookie(ownProjectCsrf).header("X-XSRF-TOKEN", ownProjectCsrf[0].getValue()))
				.andExpect(status().isOk());

		// Same session, same user, a project they have no membership in at all: denied,
		// both for read and write - a role in one project must never leak into another.
		mockMvc.perform(get("/api/v1/projects/" + OTHER_PROJECT_KEY + "/ping").cookie(session))
				.andExpect(status().isForbidden());
		Cookie[] otherProjectCsrf = obtainCsrfCookie();
		mockMvc.perform(post("/api/v1/projects/" + OTHER_PROJECT_KEY + "/ping").cookie(session)
				.cookie(otherProjectCsrf).header("X-XSRF-TOKEN", otherProjectCsrf[0].getValue()))
				.andExpect(status().isForbidden());
	}

	// --- SDK ---

	@Test
	void validServerKeyReachesTheSdkFixtureButNothingElseDoes() throws Exception {
		String serverKey = sdkCredentialService.issueServerKey(newCredential());

		mockMvc.perform(get("/api/v1/sdk/ping").header(SecurityConstants.AUTHORIZATION_HEADER,
				SecurityConstants.BEARER_PREFIX + serverKey)).andExpect(status().isOk());

		// No credential at all -> the SDK chain's own entry point rejects it.
		mockMvc.perform(get("/api/v1/sdk/ping")).andExpect(status().isUnauthorized());
	}

	@Test
	void garbageSdkCredentialIsRejected() throws Exception {
		mockMvc.perform(get("/api/v1/sdk/ping").header(SecurityConstants.AUTHORIZATION_HEADER,
				SecurityConstants.BEARER_PREFIX + "sdk-server-not-a-real-credential"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void inactiveSdkCredentialIsRejected() throws Exception {
		SdkCredential credential = newCredential();
		credential.setActive(false);
		String serverKey = sdkCredentialService.issueServerKey(credential);

		mockMvc.perform(get("/api/v1/sdk/ping").header(SecurityConstants.AUTHORIZATION_HEADER,
				SecurityConstants.BEARER_PREFIX + serverKey)).andExpect(status().isUnauthorized());
	}

	@Test
	void expiredSdkCredentialIsRejected() throws Exception {
		SdkCredential credential = newCredential();
		credential.setExpiresAt(Instant.now().minusSeconds(60));
		String serverKey = sdkCredentialService.issueServerKey(credential);

		mockMvc.perform(get("/api/v1/sdk/ping").header(SecurityConstants.AUTHORIZATION_HEADER,
				SecurityConstants.BEARER_PREFIX + serverKey)).andExpect(status().isUnauthorized());
	}

	@Test
	void notYetExpiredSdkCredentialStillAuthenticates() throws Exception {
		SdkCredential credential = newCredential();
		credential.setExpiresAt(Instant.now().plusSeconds(3600));
		String serverKey = sdkCredentialService.issueServerKey(credential);

		mockMvc.perform(get("/api/v1/sdk/ping").header(SecurityConstants.AUTHORIZATION_HEADER,
				SecurityConstants.BEARER_PREFIX + serverKey)).andExpect(status().isOk());
	}

	@Test
	void clientSideIdAuthenticatesButCannotReachAServerOnlyRoute() throws Exception {
		String clientSideId = sdkCredentialService.issueClientSideId(newCredential());

		// Authenticated (it's a real, active credential) but forbidden: the chain default
		// and the fixture route both require SDK_SERVER specifically - see SdkCredentialType.
		mockMvc.perform(get("/api/v1/sdk/ping").header(SecurityConstants.AUTHORIZATION_HEADER,
				SecurityConstants.BEARER_PREFIX + clientSideId)).andExpect(status().isForbidden());
	}

	// --- Isolation ---

	@Test
	void dashboardSessionNeverGrantsSdkAccessAndSdkKeyNeverGrantsDashboardAccess() throws Exception {
		seedUser("editor@example.com", Role.EDITOR, true);
		Cookie[] csrf = obtainCsrfCookie();
		Cookie editorSession = login("editor@example.com", csrf);

		String serverKey = sdkCredentialService.issueServerKey(newCredential());

		// A real dashboard session cookie, presented to the SDK-only route: the SDK
		// chain never reads cookies at all, so this is simply unauthenticated.
		mockMvc.perform(get("/api/v1/sdk/ping").cookie(editorSession)).andExpect(status().isUnauthorized());

		// A real SDK key, presented as if it were dashboard auth: the dashboard
		// chain never reads Authorization headers, so this is simply unauthenticated.
		mockMvc.perform(get("/api/v1/projects/" + PROJECT_KEY + "/ping")
				.header(SecurityConstants.AUTHORIZATION_HEADER, SecurityConstants.BEARER_PREFIX + serverKey))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void actuatorHealthIsPublicButAnUnmatchedPathIsDeniedByDefault() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());

		// 401, not 403: the requester is anonymous (no credential presented at all), and
		// ExceptionTranslationFilter treats a denyAll() hit by an anonymous principal as
		// "needs to authenticate" rather than "authenticated but forbidden" - matching
		// this app's own 401-vs-403 convention (401 = unauthenticated).
		mockMvc.perform(get("/some/unmatched/path")).andExpect(status().isUnauthorized());
	}

	// --- helpers ---

	private Cookie login(String email, Cookie[] csrf) throws Exception {
		MvcResult result = mockMvc
				.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
						.cookie(csrf).header("X-XSRF-TOKEN", csrf[0].getValue())
						.content(loginJson(email, PASSWORD)))
				.andExpect(status().isOk()).andReturn();

		return result.getResponse().getCookie(SecurityConstants.SESSION_COOKIE_NAME);
	}

	private Cookie[] obtainCsrfCookie() throws Exception {
		MvcResult result = mockMvc.perform(get("/api/v1/auth/session")).andReturn();
		Cookie csrf = result.getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).isNotNull();

		return new Cookie[] { csrf };
	}

	private void seedUser(String email, Role role, boolean active) {
		User user = new User();
		user.setName(email);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setActive(active);
		User saved = userRepository.save(user);

		ProjectMembership membership = new ProjectMembership();
		membership.setUserId(saved.getId());
		membership.setProjectKey(PROJECT_KEY);
		membership.setRole(role);
		projectMembershipRepository.save(membership);
	}

	private SdkCredential newCredential() {
		SdkCredential credential = new SdkCredential();
		credential.setProjectKey(PROJECT_KEY);
		credential.setEnvironmentKey("production");

		return credential;
	}

	private String loginJson(String email, String password) {
		return "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
	}
}

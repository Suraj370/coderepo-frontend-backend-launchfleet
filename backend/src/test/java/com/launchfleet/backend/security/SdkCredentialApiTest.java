package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectMembership;
import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.sdk.SdkCredentialRepository;
import com.launchfleet.backend.users.Role;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserRepository;

import jakarta.servlet.http.Cookie;

/** Integration coverage for SDK credential (API key) issuance/listing/revocation: RBAC, secret-once contract. */
@SpringBootTest
@AutoConfigureMockMvc
class SdkCredentialApiTest {

	private static final String PROJECT_KEY = "api-key-test";

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
	private SdkCredentialRepository sdkCredentialRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		sdkCredentialRepository.deleteAll();
		projectRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();

		Project project = new Project();
		project.setKey(PROJECT_KEY);
		project.setName(PROJECT_KEY);
		projectRepository.save(project);
	}

	@Test
	void adminCanIssueAServerKeyAndTheSecretIsReturnedOnlyOnce() throws Exception {
		Cookie admin = loginAsMember("admin@example.com", Role.ADMIN);

		String plaintext = mockMvc
				.perform(withCsrf(post(apiKeysPath()), admin).contentType(MediaType.APPLICATION_JSON).content("""
						{"environmentKey":"production","type":"SERVER","label":"Backend key"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.plaintextSecret").exists())
				.andExpect(jsonPath("$.data.clientSideId").doesNotExist())
				.andReturn().getResponse().getContentAsString();

		assertThat(plaintext).contains("sdk-server-");

		mockMvc.perform(get(apiKeysPath()).cookie(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].plaintextSecret").doesNotExist());
	}

	@Test
	void clientSideIdIsAlwaysVisibleSinceItIsNotSecret() throws Exception {
		Cookie admin = loginAsMember("admin2@example.com", Role.ADMIN);

		mockMvc.perform(withCsrf(post(apiKeysPath()), admin).contentType(MediaType.APPLICATION_JSON).content("""
				{"environmentKey":"production","type":"CLIENT_SIDE","label":"Web client"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.clientSideId").exists());

		mockMvc.perform(get(apiKeysPath()).cookie(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data[0].clientSideId").exists());
	}

	@Test
	void revokingMakesActiveFalse() throws Exception {
		Cookie admin = loginAsMember("admin3@example.com", Role.ADMIN);

		String id = mockMvc
				.perform(withCsrf(post(apiKeysPath()), admin).contentType(MediaType.APPLICATION_JSON).content("""
						{"environmentKey":"production","type":"SERVER","label":"To revoke"}"""))
				.andReturn().getResponse().getContentAsString();
		String credentialId = id.split("\"id\":\"")[1].split("\"")[0];

		mockMvc.perform(withCsrf(post(apiKeysPath() + "/" + credentialId + "/revoke"), admin))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.active").value(false))
				.andExpect(jsonPath("$.data.revokedAt").exists());
	}

	@Test
	void viewerCannotIssueOrListApiKeys() throws Exception {
		Cookie viewer = loginAsMember("viewer@example.com", Role.VIEWER);

		mockMvc.perform(get(apiKeysPath()).cookie(viewer)).andExpect(status().isForbidden());
		mockMvc.perform(withCsrf(post(apiKeysPath()), viewer).contentType(MediaType.APPLICATION_JSON).content("""
				{"environmentKey":"production","type":"SERVER","label":"Blocked"}"""))
				.andExpect(status().isForbidden());
	}

	private String apiKeysPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/api-keys";
	}

	private Cookie loginAsMember(String email, Role role) throws Exception {
		User user = new User();
		user.setName(email);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setActive(true);
		User saved = userRepository.save(user);

		ProjectMembership membership = new ProjectMembership();
		membership.setUserId(saved.getId());
		membership.setProjectKey(PROJECT_KEY);
		membership.setRole(role);
		projectMembershipRepository.save(membership);

		Cookie[] csrf = obtainCsrfCookie();
		MvcResult result = mockMvc
				.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).cookie(csrf)
						.header("X-XSRF-TOKEN", csrf[0].getValue())
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

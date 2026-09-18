package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.launchfleet.backend.projects.ProjectMembership;
import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.users.Role;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserRepository;

import jakarta.servlet.http.Cookie;

/** Integration coverage for Project create/list/rename: membership grant on create, own-projects-only listing, RBAC. */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectApiTest {

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
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		projectRepository.deleteAll();
		projectMembershipRepository.deleteAll();
		userRepository.deleteAll();
	}

	@Test
	void creatingAProjectGrantsTheCreatorAdmin() throws Exception {
		Cookie session = registerAndLogin("founder@example.com");

		mockMvc.perform(withCsrf(post("/api/v1/projects"), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"acme","name":"Acme"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.key").value("acme"))
				.andExpect(jsonPath("$.data.role").value("ADMIN"));

		mockMvc.perform(get("/api/v1/projects").cookie(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].key").value("acme"));
	}

	@Test
	void duplicateProjectKeyIsRejected() throws Exception {
		Cookie session = registerAndLogin("dup@example.com");

		mockMvc.perform(withCsrf(post("/api/v1/projects"), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"dup-key","name":"First"}""")).andExpect(status().isOk());

		mockMvc.perform(withCsrf(post("/api/v1/projects"), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"dup-key","name":"Second"}""")).andExpect(status().isConflict());
	}

	@Test
	void listOnlyShowsProjectsTheCallerIsAMemberOf() throws Exception {
		Cookie mine = registerAndLogin("mine@example.com");
		Cookie theirs = registerAndLogin("theirs@example.com");

		mockMvc.perform(withCsrf(post("/api/v1/projects"), mine).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"mine-project","name":"Mine"}""")).andExpect(status().isOk());
		mockMvc.perform(withCsrf(post("/api/v1/projects"), theirs).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"their-project","name":"Theirs"}""")).andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/projects").cookie(mine)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(1))
				.andExpect(jsonPath("$.data[0].key").value("mine-project"));
	}

	@Test
	void renameRequiresAdmin() throws Exception {
		Cookie admin = registerAndLogin("admin@example.com");
		mockMvc.perform(withCsrf(post("/api/v1/projects"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"key":"renamable","name":"Old Name"}""")).andExpect(status().isOk());

		mockMvc.perform(withCsrf(patch("/api/v1/projects/renamable"), admin).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"New Name"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("New Name"));

		Cookie viewer = loginAsMember("viewer@example.com", Role.VIEWER, "renamable");
		mockMvc.perform(withCsrf(patch("/api/v1/projects/renamable"), viewer).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"Blocked"}""")).andExpect(status().isForbidden());
	}

	@Test
	void noSessionAtAllIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
	}

	private Cookie registerAndLogin(String email) throws Exception {
		User user = new User();
		user.setName(email);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setActive(true);
		userRepository.save(user);

		Cookie[] csrf = obtainCsrfCookie();
		MvcResult result = mockMvc
				.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).cookie(csrf)
						.header("X-XSRF-TOKEN", csrf[0].getValue())
						.content("{\"email\":\"" + email + "\",\"password\":\"" + PASSWORD + "\"}"))
				.andExpect(status().isOk()).andReturn();

		return result.getResponse().getCookie(SecurityConstants.SESSION_COOKIE_NAME);
	}

	private Cookie loginAsMember(String email, Role role, String projectKey) throws Exception {
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

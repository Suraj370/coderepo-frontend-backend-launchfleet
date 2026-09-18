package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.launchfleet.backend.projects.Project;
import com.launchfleet.backend.projects.ProjectMembership;
import com.launchfleet.backend.projects.ProjectMembershipRepository;
import com.launchfleet.backend.projects.ProjectRepository;
import com.launchfleet.backend.users.Role;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserRepository;

import jakarta.servlet.http.Cookie;

/** Integration coverage for team membership: add/update/remove, RBAC, and the "never zero ADMINs" rule. */
@SpringBootTest
@AutoConfigureMockMvc
class ProjectMembershipApiTest {

	private static final String PROJECT_KEY = "membership-test";

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

		Project project = new Project();
		project.setKey(PROJECT_KEY);
		project.setName(PROJECT_KEY);
		projectRepository.save(project);
	}

	@Test
	void adminCanAddAnExistingUserAsAMember() throws Exception {
		Cookie admin = loginAsMember("admin@example.com", Role.ADMIN);
		registerOnly("newmember@example.com");

		mockMvc.perform(withCsrf(post(membersPath()), admin).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"newmember@example.com","role":"EDITOR"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("newmember@example.com"))
				.andExpect(jsonPath("$.data.role").value("EDITOR"));

		mockMvc.perform(get(membersPath()).cookie(admin)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.length()").value(2));
	}

	@Test
	void addingAnUnregisteredEmailIs404() throws Exception {
		Cookie admin = loginAsMember("admin2@example.com", Role.ADMIN);

		mockMvc.perform(withCsrf(post(membersPath()), admin).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"nobody@example.com","role":"VIEWER"}""")).andExpect(status().isNotFound());
	}

	@Test
	void addingAnAlreadyExistingMemberIsConflict() throws Exception {
		Cookie admin = loginAsMember("admin3@example.com", Role.ADMIN);

		mockMvc.perform(withCsrf(post(membersPath()), admin).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"admin3@example.com","role":"VIEWER"}""")).andExpect(status().isConflict());
	}

	@Test
	void viewerCanListButNotAdd() throws Exception {
		Cookie viewer = loginAsMember("viewer@example.com", Role.VIEWER);

		mockMvc.perform(get(membersPath()).cookie(viewer)).andExpect(status().isOk());
		mockMvc.perform(withCsrf(post(membersPath()), viewer).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"viewer@example.com","role":"EDITOR"}""")).andExpect(status().isForbidden());
	}

	@Test
	void demotingTheLastAdminIsRejected() throws Exception {
		Cookie admin = loginAsMember("soleadmin@example.com", Role.ADMIN);
		String membershipId = projectMembershipRepository
				.findByUserIdAndProjectKey(userRepository.findByEmailAndActiveTrue("soleadmin@example.com").get().getId(),
						PROJECT_KEY)
				.get().getId();

		mockMvc.perform(withCsrf(patch(membersPath() + "/" + membershipId), admin).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"role":"EDITOR"}""")).andExpect(status().isConflict());
	}

	@Test
	void removingTheLastAdminIsRejectedButASecondAdminCanBeRemoved() throws Exception {
		Cookie admin = loginAsMember("firstadmin@example.com", Role.ADMIN);
		registerOnly("secondadmin@example.com");
		mockMvc.perform(withCsrf(post(membersPath()), admin).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"email":"secondadmin@example.com","role":"ADMIN"}""")).andExpect(status().isOk());

		String firstAdminMembershipId = projectMembershipRepository
				.findByUserIdAndProjectKey(userRepository.findByEmailAndActiveTrue("firstadmin@example.com").get().getId(),
						PROJECT_KEY)
				.get().getId();
		String secondAdminMembershipId = projectMembershipRepository
				.findByUserIdAndProjectKey(
						userRepository.findByEmailAndActiveTrue("secondadmin@example.com").get().getId(), PROJECT_KEY)
				.get().getId();

		// Two ADMINs exist - removing one is fine.
		mockMvc.perform(withCsrf(delete(membersPath() + "/" + secondAdminMembershipId), admin))
				.andExpect(status().isOk());

		// Now only one ADMIN remains - removing it is rejected.
		mockMvc.perform(withCsrf(delete(membersPath() + "/" + firstAdminMembershipId), admin))
				.andExpect(status().isConflict());
	}

	private String membersPath() {
		return "/api/v1/projects/" + PROJECT_KEY + "/members";
	}

	private void registerOnly(String email) {
		User user = new User();
		user.setName(email);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setActive(true);
		userRepository.save(user);
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

package com.launchfleet.backend.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserRepository;

import jakarta.servlet.http.Cookie;

/** Integration coverage for the current user's own profile: get, update name, validation, no-session. */
@SpringBootTest
@AutoConfigureMockMvc
class UserProfileApiTest {

	private static final String PASSWORD = "correct horse battery staple";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		userRepository.deleteAll();
	}

	@Test
	void getMyProfile() throws Exception {
		Cookie session = registerAndLogin("profile1@example.com", "Original Name");

		mockMvc.perform(get("/api/v1/users/me").cookie(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.email").value("profile1@example.com"))
				.andExpect(jsonPath("$.data.name").value("Original Name"));
	}

	@Test
	void updateMyName() throws Exception {
		Cookie session = registerAndLogin("profile2@example.com", "Old Name");

		mockMvc.perform(withCsrf(patch("/api/v1/users/me"), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":"New Name"}"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("New Name"));

		mockMvc.perform(get("/api/v1/users/me").cookie(session)).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.name").value("New Name"));
	}

	@Test
	void blankNameIsRejected() throws Exception {
		Cookie session = registerAndLogin("profile3@example.com", "Some Name");

		mockMvc.perform(withCsrf(patch("/api/v1/users/me"), session).contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"name":""}""")).andExpect(status().isBadRequest());
	}

	@Test
	void noSessionAtAllIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized());
	}

	private Cookie registerAndLogin(String email, String name) throws Exception {
		User user = new User();
		user.setName(name);
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(PASSWORD));
		user.setActive(true);
		userRepository.save(user);

		Cookie[] csrf = obtainCsrfCookie();
		MvcResult result = mockMvc
				.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON).cookie(csrf).header("X-XSRF-TOKEN", csrf[0].getValue())
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

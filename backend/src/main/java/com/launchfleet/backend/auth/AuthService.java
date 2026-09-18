package com.launchfleet.backend.auth;

import java.util.Map;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

import com.launchfleet.backend.shared.ApiException;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserPrincipal;
import com.launchfleet.backend.users.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class AuthService {

	private static final int UNAUTHORIZED = 401;

	private static final int CONFLICT = 409;

	private final AuthenticationManager authenticationManager;

	private final SecurityContextRepository securityContextRepository;

	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	public AuthService(AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository,
			SessionAuthenticationStrategy sessionAuthenticationStrategy, UserRepository userRepository,
			PasswordEncoder passwordEncoder) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	public Map<String, Object> login(HttpServletRequest request, HttpServletResponse response,
			LoginRequest loginRequest) {
		Authentication authenticated;

		try {
			authenticated = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(loginRequest.email().toLowerCase(),
							loginRequest.password()));
		} catch (AuthenticationException exception) {
			throw new ApiException(UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.");
		}

		return establishSession(authenticated, request, response);
	}

	public Map<String, Object> register(HttpServletRequest request, HttpServletResponse response,
			RegisterRequest registerRequest) {
		String email = registerRequest.email().toLowerCase();

		if (userRepository.existsByEmail(email)) {
			throw new ApiException(CONFLICT, "EMAIL_TAKEN", "An account with this email already exists.");
		}

		User user = new User();
		user.setName(registerRequest.name());
		user.setEmail(email);
		user.setPasswordHash(passwordEncoder.encode(registerRequest.password()));

		User saved = userRepository.save(user);
		UserPrincipal principal = new UserPrincipal(saved);

		// The account was just created with a known-good password, so there is no
		// credential to re-verify - build the authenticated token directly rather than
		// round-tripping through AuthenticationManager as login() does.
		Authentication authenticated = UsernamePasswordAuthenticationToken.authenticated(principal, null,
				principal.getAuthorities());

		return establishSession(authenticated, request, response);
	}

	public Map<String, Object> session(Authentication authentication) {
		UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();

		return Map.of("user", principal.getUser().toPublicMap());
	}

	/**
	 * Shared tail of login and register: both bypass Spring Security's standard
	 * AbstractAuthenticationProcessingFilter flow, which is what would otherwise trigger
	 * session-fixation protection automatically (see SecurityConfig.sessionAuthenticationStrategy's
	 * Javadoc) - invoked by hand here, on the still-unauthenticated request session,
	 * before the new context is attached.
	 */
	private Map<String, Object> establishSession(Authentication authenticated, HttpServletRequest request,
			HttpServletResponse response) {
		sessionAuthenticationStrategy.onAuthentication(authenticated, request, response);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authenticated);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, request, response);

		return session(authenticated);
	}
}

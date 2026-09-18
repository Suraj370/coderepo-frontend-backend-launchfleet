package com.launchfleet.backend.users;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.launchfleet.backend.security.SecurityConstants;

/**
 * Wraps a {@link User} for Spring Security. Only carries the baseline
 * "authenticated dashboard user" authority - per-project roles are looked up
 * fresh per request (see ProjectAccessService) rather than cached here, so a
 * role change takes effect without waiting for the session to be reissued.
 */
public class UserPrincipal implements UserDetails {

	private final User user;

	public UserPrincipal(User user) {
		this.user = user;
	}

	public String getId() {
		return user.getId();
	}

	public User getUser() {
		return user;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of(new SimpleGrantedAuthority(SecurityConstants.DASHBOARD_USER_AUTHORITY));
	}

	@Override
	public String getPassword() {
		return user.getPasswordHash();
	}

	@Override
	public String getUsername() {
		return user.getEmail();
	}

	@Override
	public boolean isEnabled() {
		return user.isActive();
	}
}

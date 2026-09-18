package com.launchfleet.backend.security.sdk;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.launchfleet.backend.sdk.SdkCredential;
import com.launchfleet.backend.sdk.SdkCredentialType;
import com.launchfleet.backend.security.SecurityConstants;

/**
 * The Authentication placed on the SecurityContext by SdkCredentialAuthenticationFilter.
 * Its authorities are SDK_SERVER / SDK_CLIENT_SIDE - never ROLE_*, and its principal is
 * the credential's project/environment, never a User - so it structurally cannot satisfy
 * any dashboard hasRole/@PreAuthorize check, no matter what gets misconfigured downstream.
 */
public class SdkAuthenticationToken extends AbstractAuthenticationToken {

	private final SdkCredential credential;

	public SdkAuthenticationToken(SdkCredential credential) {
		super(List.of(new SimpleGrantedAuthority(authorityFor(credential.getType()))));
		this.credential = credential;
		setAuthenticated(true);
	}

	private static String authorityFor(SdkCredentialType type) {
		return switch (type) {
			case SERVER -> SecurityConstants.SDK_SERVER_AUTHORITY;
			case CLIENT_SIDE -> SecurityConstants.SDK_CLIENT_SIDE_AUTHORITY;
		};
	}

	@Override
	public Object getCredentials() {
		return null;
	}

	@Override
	public Object getPrincipal() {
		return credential;
	}
}

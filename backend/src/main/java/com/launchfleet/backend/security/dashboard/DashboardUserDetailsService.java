package com.launchfleet.backend.security.dashboard;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserPrincipal;
import com.launchfleet.backend.users.UserRepository;

@Service
public class DashboardUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	public DashboardUserDetailsService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public UserDetails loadUserByUsername(String email) {
		User user = userRepository.findByEmailAndActiveTrue(email.toLowerCase())
				.orElseThrow(() -> new UsernameNotFoundException("No active user with that email."));

		return new UserPrincipal(user);
	}
}

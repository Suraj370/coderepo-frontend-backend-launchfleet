package com.launchfleet.backend.users.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.shared.ApiException;
import com.launchfleet.backend.users.User;
import com.launchfleet.backend.users.UserRepository;

@Component
public class UpdateProfile {

	private static final int BAD_REQUEST = 400;

	private final UserRepository userRepository;

	public UpdateProfile(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public User execute(User user, String name) {
		if (name == null || name.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "name is required.");
		}

		user.setName(name);

		return userRepository.save(user);
	}
}

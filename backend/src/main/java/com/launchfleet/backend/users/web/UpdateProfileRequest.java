package com.launchfleet.backend.users.web;

import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(

		@NotBlank String name) {
}

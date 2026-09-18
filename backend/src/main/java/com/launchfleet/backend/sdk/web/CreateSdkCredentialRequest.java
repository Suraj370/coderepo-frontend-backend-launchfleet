package com.launchfleet.backend.sdk.web;

import com.launchfleet.backend.sdk.SdkCredentialType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSdkCredentialRequest(

		@NotBlank String environmentKey,

		@NotNull SdkCredentialType type,

		@NotBlank String label) {
}

package com.launchfleet.backend.sdk.application;

import org.springframework.stereotype.Component;

import com.launchfleet.backend.sdk.SdkCredential;
import com.launchfleet.backend.sdk.SdkCredentialService;
import com.launchfleet.backend.sdk.SdkCredentialType;
import com.launchfleet.backend.shared.ApiException;

@Component
public class CreateSdkCredential {

	private static final int BAD_REQUEST = 400;

	private final SdkCredentialService sdkCredentialService;

	public CreateSdkCredential(SdkCredentialService sdkCredentialService) {
		this.sdkCredentialService = sdkCredentialService;
	}

	/** The plaintext secret (SERVER) or client-side id (CLIENT_SIDE) - never recoverable again. */
	public record Result(SdkCredential credential, String plaintext) {
	}

	public Result execute(String projectKey, String environmentKey, SdkCredentialType type, String label) {
		if (label == null || label.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "label is required.");
		}
		if (environmentKey == null || environmentKey.isBlank()) {
			throw new ApiException(BAD_REQUEST, "VALIDATION_ERROR", "environmentKey is required.");
		}

		SdkCredential credential = new SdkCredential();
		credential.setProjectKey(projectKey);
		credential.setEnvironmentKey(environmentKey);
		credential.setLabel(label);

		String plaintext = type == SdkCredentialType.SERVER ? sdkCredentialService.issueServerKey(credential)
				: sdkCredentialService.issueClientSideId(credential);

		return new Result(credential, plaintext);
	}
}

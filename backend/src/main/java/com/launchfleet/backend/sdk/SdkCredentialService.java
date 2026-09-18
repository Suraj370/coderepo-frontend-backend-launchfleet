package com.launchfleet.backend.sdk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

import org.springframework.stereotype.Service;

/**
 * The single home for SDK credential business logic: generation, hashing, and lookup.
 * Spring-Security-specific wiring (the authentication filter/token) lives under
 * security/sdk and calls only {@link #authenticate(String)} - it never touches the
 * repository or the hashing logic directly.
 */
@Service
public class SdkCredentialService {

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int SECRET_BYTES = 32;

	private final SdkCredentialRepository sdkCredentialRepository;

	public SdkCredentialService(SdkCredentialRepository sdkCredentialRepository) {
		this.sdkCredentialRepository = sdkCredentialRepository;
	}

	/** Returns the plaintext secret. It is never recoverable again - only its hash is stored. */
	public String issueServerKey(SdkCredential credential) {
		String secret = "sdk-server-" + randomToken();
		credential.setType(SdkCredentialType.SERVER);
		credential.setSecretHash(hash(secret));
		sdkCredentialRepository.save(credential);

		return secret;
	}

	/** Returns the client-side id, which is not secret and is stored in plaintext. */
	public String issueClientSideId(SdkCredential credential) {
		String clientSideId = "sdk-client-" + randomToken();
		credential.setType(SdkCredentialType.CLIENT_SIDE);
		credential.setClientSideId(clientSideId);
		sdkCredentialRepository.save(credential);

		return clientSideId;
	}

	/** Terminal - a revoked credential can never authenticate again (see {@link #authenticate}). */
	public void revoke(SdkCredential credential) {
		credential.setActive(false);
		credential.setRevokedAt(Instant.now());
		sdkCredentialRepository.save(credential);
	}

	/**
	 * Resolves a raw bearer value (as presented by an SDK client) to its credential.
	 * Tries a hashed lookup against server secrets first, then falls back to an exact
	 * match against non-secret client-side ids. Empty when the credential is unknown,
	 * inactive (revoked; active=false), expired, or garbage - the caller (the filter)
	 * doesn't need to know or care which.
	 */
	public Optional<SdkCredential> authenticate(String rawCredential) {
		Optional<SdkCredential> serverKey = sdkCredentialRepository.findBySecretHashAndActiveTrue(hash(rawCredential));
		Optional<SdkCredential> candidate = serverKey.isPresent() ? serverKey
				: sdkCredentialRepository.findByClientSideIdAndActiveTrue(rawCredential);

		return candidate.filter(SdkCredentialService::isNotExpired);
	}

	private static boolean isNotExpired(SdkCredential credential) {
		Instant expiresAt = credential.getExpiresAt();

		return expiresAt == null || expiresAt.isAfter(Instant.now());
	}

	/**
	 * SHA-256, not BCrypt: the secret is a 256-bit random value with no guessable
	 * structure, so a slow adaptive hash defends against nothing a fast one doesn't -
	 * and credential lookup needs a deterministic, indexable hash (SdkCredential.secretHash
	 * has a unique index) rather than BCrypt's per-call salted verification, which can't be
	 * looked up by equality at all. This is the opposite trade-off from human passwords,
	 * which BCryptPasswordEncoder handles elsewhere for exactly that reason.
	 */
	private String hash(String secret) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");

			return HexFormat.of().formatHex(digest.digest(secret.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is not available.", exception);
		}
	}

	private String randomToken() {
		byte[] bytes = new byte[SECRET_BYTES];
		RANDOM.nextBytes(bytes);

		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}

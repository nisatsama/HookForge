
package com.hookforge.security;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class WebhookSecretEncryptionService {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int NONCE_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookSecretEncryptionService() {
        String encodedKey = System.getenv("HOOKFORGE_ENCRYPTION_KEY");

        if (encodedKey == null || encodedKey.isBlank()) {
            throw new IllegalStateException(
                    "HOOKFORGE_ENCRYPTION_KEY environment variable is required"
            );
        }

        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);

            if (keyBytes.length != 32) {
                throw new IllegalStateException(
                        "HOOKFORGE_ENCRYPTION_KEY must decode to exactly 32 bytes"
                );
            }

            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "HOOKFORGE_ENCRYPTION_KEY must be valid Base64",
                    ex
            );
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            throw new IllegalArgumentException(
                    "Secret cannot be null or blank"
            );
        }

        try {
            byte[] nonce = new byte[NONCE_LENGTH];
            secureRandom.nextBytes(nonce);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
            );

            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8)
            );

            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

            return encoder.encodeToString(nonce)
                    + "."
                    + encoder.encodeToString(ciphertext);

        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException(
                    "Could not encrypt webhook secret",
                    ex
            );
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            throw new IllegalArgumentException(
                    "Encrypted secret cannot be null or blank"
            );
        }

        try {
            String[] parts = encryptedValue.split("\\.", -1);

            if (parts.length != 2) {
                throw new IllegalArgumentException(
                        "Invalid encrypted-secret format"
                );
            }

            Base64.Decoder decoder = Base64.getUrlDecoder();
            byte[] nonce = decoder.decode(parts[0]);
            byte[] ciphertext = decoder.decode(parts[1]);

            if (nonce.length != NONCE_LENGTH) {
                throw new IllegalArgumentException(
                        "Invalid encryption nonce length"
                );
            }

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    key,
                    new GCMParameterSpec(TAG_LENGTH_BITS, nonce)
            );

            byte[] plaintext = cipher.doFinal(ciphertext);

            return new String(
                    plaintext,
                    java.nio.charset.StandardCharsets.UTF_8
            );

        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Could not decrypt webhook secret; check the encryption key and stored value",
                    ex
            );
        }
    }
}


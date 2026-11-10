package com.kyro.platform.common.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption for secrets at rest (TOTP seeds). Output is {@code base64(iv || ciphertext+tag)}.
 * The key is {@code kyro.mfa.encryption-key}; startup fails when it is missing outside the local and test profiles.
 */
@Component
public class SecretCipher {

    public static final String KEY_ID = "v1";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int IV_BYTES = 12;

    private final SecretKeySpec key;

    public SecretCipher(MfaProperties props, Environment env) {
        String configured = props.encryptionKey();
        byte[] raw;
        if (configured == null || configured.isBlank()) {
            if (!env.acceptsProfiles(Profiles.of("local", "test"))) {
                throw new IllegalStateException("kyro.mfa.encryption-key (KYRO_MFA_ENCRYPTION_KEY) is required outside local/test");
            }
            raw = new byte[32];
            RANDOM.nextBytes(raw); // ephemeral: secrets would not survive a restart
        } else {
            raw = Base64.getDecoder().decode(configured.trim());
            if (raw.length != 32) {
                throw new IllegalStateException("kyro.mfa.encryption-key must be a base64 encoded 32 byte key");
            }
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] all = Base64.getDecoder().decode(encoded);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("decryption failed", e);
        }
    }
}

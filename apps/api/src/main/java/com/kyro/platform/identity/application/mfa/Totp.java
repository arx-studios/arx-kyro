package com.kyro.platform.identity.application.mfa;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** RFC 6238 TOTP (HMAC-SHA1, 6 digits, 30 second step) and RFC 4648 base32, JDK crypto only. */
public final class Totp {

    public static final int STEP_SECONDS = 30;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private Totp() {}

    public static String newSecret() {
        byte[] raw = new byte[20];
        RANDOM.nextBytes(raw);
        return base32Encode(raw);
    }

    public static long stepAt(Instant instant) {
        return Math.floorDiv(instant.getEpochSecond(), STEP_SECONDS);
    }

    public static String code(String base32Secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(base32Secret), "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Finds the step whose code equals {@code submitted} within +-{@code skew} steps of {@code now} and strictly after
     * {@code lastUsedStep} (replay protection). Compares in constant time.
     */
    public static OptionalLong match(String base32Secret, String submitted, Instant now, int skew, long lastUsedStep) {
        long current = stepAt(now);
        byte[] given = submitted.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        OptionalLong found = OptionalLong.empty();
        for (long s = current - skew; s <= current + skew; s++) {
            boolean equal = MessageDigest.isEqual(code(base32Secret, s).getBytes(java.nio.charset.StandardCharsets.US_ASCII), given);
            if (equal && s > lastUsedStep && (found.isEmpty() || s > found.getAsLong())) {
                found = OptionalLong.of(s);
            }
        }
        return found;
    }

    public static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    public static byte[] base32Decode(String s) {
        String in = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(in.length() * 5 / 8 + 1);
        int buffer = 0;
        int bits = 0;
        for (char c : in.toCharArray()) {
            int v = BASE32.indexOf(c);
            if (v < 0) {
                throw new IllegalArgumentException("invalid base32");
            }
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        byte[] result = new byte[out.position()];
        out.flip();
        out.get(result);
        return result;
    }
}

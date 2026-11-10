package com.kyro.platform.identity.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import com.kyro.platform.identity.application.mfa.Totp;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class TotpTest {

    /** RFC 6238 appendix B secret "12345678901234567890" (SHA-1); the 6 digit codes are the last 6 of the 8 digit vectors. */
    private static final String RFC_SECRET = Totp.base32Encode("12345678901234567890".getBytes(StandardCharsets.US_ASCII));

    @Test
    void matchesRfc6238Vectors() {
        assertThat(Totp.code(RFC_SECRET, 59L / 30)).isEqualTo("287082");
        assertThat(Totp.code(RFC_SECRET, 1111111109L / 30)).isEqualTo("081804");
        assertThat(Totp.code(RFC_SECRET, 1234567890L / 30)).isEqualTo("005924");
        assertThat(Totp.code(RFC_SECRET, 2000000000L / 30)).isEqualTo("279037");
    }

    @Test
    void base32RoundTrips() {
        byte[] raw = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20};
        assertThat(Totp.base32Decode(Totp.base32Encode(raw))).isEqualTo(raw);
        assertThat(Totp.newSecret()).hasSize(32).matches("[A-Z2-7]+");
    }

    @Test
    void acceptsCodesWithinTheSkewWindowOnly() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long step = Totp.stepAt(now);
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step), now, 1, -1)).hasValue(step);
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, 1, -1)).hasValue(step - 1);
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, 1, -1)).hasValue(step + 1);
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step - 2), now, 1, -1)).isEmpty();
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step + 2), now, 1, -1)).isEmpty();
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, 0, -1)).isEmpty();
    }

    @Test
    void rejectsReplayOfAnAlreadyUsedStep() {
        Instant now = Instant.ofEpochSecond(1_700_000_000L);
        long step = Totp.stepAt(now);
        String code = Totp.code(RFC_SECRET, step);
        assertThat(Totp.match(RFC_SECRET, code, now, 1, step)).isEmpty();
        assertThat(Totp.match(RFC_SECRET, code, now, 1, step + 1)).isEmpty();
        // an older code than the last used step is also refused even though it is inside the window
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step - 1), now, 1, step)).isEmpty();
        // the next step is still fine
        assertThat(Totp.match(RFC_SECRET, Totp.code(RFC_SECRET, step + 1), now, 1, step)).hasValue(step + 1);
    }
}

package com.kyro.platform.support;

import com.kyro.platform.identity.application.VerificationMailer;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/** Replaces SMTP delivery with an in-memory outbox so tests can read verification tokens. */
@TestConfiguration
public class TestMailConfig {

    public record SentMail(String to, String kind, String token) {}

    public static class CapturingMailer implements VerificationMailer {
        private final List<SentMail> sent = new CopyOnWriteArrayList<>();

        @Override
        public void sendVerification(String toEmail, String displayName, String rawToken) {
            sent.add(new SentMail(toEmail, "VERIFICATION", rawToken));
        }

        @Override
        public void sendExistingAccountNotice(String toEmail) {
            sent.add(new SentMail(toEmail, "EXISTING_ACCOUNT", null));
        }

        public List<SentMail> all() {
            return sent;
        }

        public Optional<String> lastTokenFor(String email) {
            return sent.stream()
                    .filter(m -> m.to().equalsIgnoreCase(email) && m.token() != null)
                    .reduce((a, b) -> b)
                    .map(SentMail::token);
        }

        public long countFor(String email, String kind) {
            return sent.stream().filter(m -> m.to().equalsIgnoreCase(email) && m.kind().equals(kind)).count();
        }
    }

    @Bean
    @Primary
    public CapturingMailer capturingMailer() {
        return new CapturingMailer();
    }
}

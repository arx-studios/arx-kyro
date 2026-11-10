package com.kyro.platform.identity.account;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;

/** Replaces SMTP with an in-memory outbox of {@link SimpleMailMessage}s (shared by account and notification tests). */
@TestConfiguration
public class CapturingMailSenderConfig {

    public static class CapturingMailSender extends JavaMailSenderImpl {
        private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();
        private final AtomicBoolean failing = new AtomicBoolean(false);

        @Override
        public void send(SimpleMailMessage... messages) throws MailException {
            if (failing.get()) {
                throw new MailSendException("simulated SMTP outage");
            }
            sent.addAll(List.of(messages));
        }

        public void failing(boolean value) {
            failing.set(value);
        }

        public List<SimpleMailMessage> to(String email) {
            return sent.stream()
                    .filter(m -> m.getTo() != null && List.of(m.getTo()).contains(email))
                    .toList();
        }
    }

    @Bean
    @Primary
    public CapturingMailSender capturingMailSender() {
        return new CapturingMailSender();
    }
}

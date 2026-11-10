package com.kyro.platform.identity.infrastructure;

import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.identity.application.AccountMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class SmtpAccountMailer implements AccountMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpAccountMailer.class);

    private final JavaMailSender mailSender;
    private final KyroProperties props;
    private final boolean local;

    SmtpAccountMailer(JavaMailSender mailSender, KyroProperties props, Environment env) {
        this.mailSender = mailSender;
        this.props = props;
        this.local = env.acceptsProfiles(Profiles.of("local"));
    }

    @Override
    public void sendPasswordReset(String toEmail, String displayName, String rawToken) {
        String link = props.webBaseUrl() + "/reset-password?token=" + rawToken;
        if (local) {
            log.info("DEV-ONLY password reset link for {}: {}", toEmail, link);
        }
        send(
                toEmail,
                "Reset your KYRO password",
                "Hello " + displayName + ",\n\nUse this link to choose a new KYRO password:\n" + link
                        + "\n\nThe link can be used once and expires soon. If you did not ask for this, ignore this message.\n\n"
                        + "KYRO is a sandbox: fictional data, no real money.");
    }

    @Override
    public void sendPasswordChanged(String toEmail, String displayName) {
        send(
                toEmail,
                "Your KYRO password was changed",
                "Hello " + displayName + ",\n\nYour KYRO password was just changed and other sessions were signed out. "
                        + "If this was not you, reset your password immediately.");
    }

    private void send(String to, String subject, String text) {
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(props.mailFrom());
            m.setTo(to);
            m.setSubject(subject);
            m.setText(text);
            mailSender.send(m);
        } catch (RuntimeException e) {
            log.warn("Could not send email '{}' (delivery failure ignored): {}", subject, e.getMessage());
        }
    }
}

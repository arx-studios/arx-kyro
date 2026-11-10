package com.kyro.platform.identity.infrastructure;

import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.identity.application.VerificationMailer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
class SmtpVerificationMailer implements VerificationMailer {

    private static final Logger log = LoggerFactory.getLogger(SmtpVerificationMailer.class);

    private final JavaMailSender mailSender;
    private final KyroProperties props;
    private final boolean local;

    SmtpVerificationMailer(JavaMailSender mailSender, KyroProperties props, Environment env) {
        this.mailSender = mailSender;
        this.props = props;
        this.local = env.acceptsProfiles(Profiles.of("local"));
    }

    @Override
    public void sendVerification(String toEmail, String displayName, String rawToken) {
        String link = props.webBaseUrl() + "/verify-email?token=" + rawToken;
        if (local) {
            log.info("DEV-ONLY verification link for {}: {}", toEmail, link);
        }
        send(
                toEmail,
                "Verify your KYRO email address",
                "Hello " + displayName + ",\n\nConfirm your email address to activate your KYRO sandbox account:\n"
                        + link + "\n\nThe link expires in 24 hours. If you did not register, ignore this message.\n\n"
                        + "KYRO is a sandbox: fictional data, no real money.");
    }

    @Override
    public void sendExistingAccountNotice(String toEmail) {
        send(
                toEmail,
                "Someone tried to register with your email",
                "Someone tried to register a KYRO account with this email address, but an account already exists.\n"
                        + "If this was you, simply log in. If not, you can ignore this message.");
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

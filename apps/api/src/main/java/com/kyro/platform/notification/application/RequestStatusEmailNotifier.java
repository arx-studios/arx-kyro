package com.kyro.platform.notification.application;

import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.education.application.port.EducationRequestStatusChanged;
import com.kyro.platform.identity.application.ReviewRecipientView;
import com.kyro.platform.identity.application.UserDirectory;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * E-mails the recipient when staff decide on their request. Runs only after the publishing transaction has
 * committed (a rolled-back transition sends nothing) and never propagates a failure: the state change is already
 * durable. The mail is deliberately minimal: title, plain-words status and a link. It never carries the internal
 * rationale, the staff message text, fee references or tokens.
 */
@Component
class RequestStatusEmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(RequestStatusEmailNotifier.class);

    private static final Map<String, String> STATUS_WORDS = Map.of(
            "APPROVED", "approved",
            "CHANGES_REQUESTED", "returned to you: changes are needed",
            "REJECTED", "not approved");

    private final UserDirectory users;
    private final JavaMailSender mailSender;
    private final KyroProperties props;
    private final NotificationProperties notifications;

    RequestStatusEmailNotifier(
            UserDirectory users, JavaMailSender mailSender, KyroProperties props, NotificationProperties notifications) {
        this.users = users;
        this.mailSender = mailSender;
        this.props = props;
        this.notifications = notifications;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onStatusChanged(EducationRequestStatusChanged event) {
        String words = STATUS_WORDS.get(event.toStatus());
        if (words == null || !notifications.enabled()) {
            return;
        }
        try {
            ReviewRecipientView recipient = users.findRecipient(event.recipientUserId()).orElse(null);
            if (recipient == null) {
                log.warn("Status email skipped: recipient {} not found (request {})", event.recipientUserId(), event.requestId());
                return;
            }
            String link = props.webBaseUrl() + "/recipient/requests/" + event.requestId();
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(props.mailFrom());
            m.setTo(recipient.email());
            m.setSubject("Update on your KYRO request");
            m.setText("Hello " + recipient.displayName() + ",\n\nYour request \"" + event.title() + "\" was " + words + ".\n\n"
                    + "View it here: " + link + "\n\nKYRO is a sandbox: fictional data, no real money.");
            mailSender.send(m);
        } catch (RuntimeException e) {
            log.warn("Status email for request {} could not be sent (ignored): {}", event.requestId(), e.getMessage());
        }
    }
}

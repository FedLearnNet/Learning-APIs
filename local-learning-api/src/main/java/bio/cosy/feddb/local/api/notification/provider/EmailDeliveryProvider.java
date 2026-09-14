package bio.cosy.feddb.local.api.notification.provider;

import bio.cosy.feddb.core.api.notification.NotificationBaseDTO;
import bio.cosy.feddb.core.api.notification.NotificationDeliveryProvider;
import bio.cosy.feddb.local.services.KeycloakService;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
public class EmailDeliveryProvider implements NotificationDeliveryProvider {

    @Inject
    KeycloakService keycloakService;

    @Inject
    Mailer mailer;

    @Override
    public void deliver(NotificationBaseDTO delivery) {
        if (delivery == null) {
            throw new IllegalArgumentException("Notification delivery must not be null");
        }

        String recipientMail = keycloakService.getById(delivery.getRecipientUserId()).getEmail();
        if (StringUtils.isBlank(recipientMail)) {
            throw new IllegalStateException("Recipient user has no email address: " + delivery.getRecipientUserId());
        }

        Mail mail = Mail.withText(
                recipientMail,
                buildSubject(delivery),
                buildBody(delivery)
        );

        mailer.send(mail);
    }

    private String buildSubject(NotificationBaseDTO delivery) {
        if (StringUtils.isNotBlank(delivery.getTitle())) {
            return delivery.getTitle();
        }
        return "FL-Net notification";
    }

    private String buildBody(NotificationBaseDTO delivery) {
        StringBuilder body = new StringBuilder();

        if (StringUtils.isNotBlank(delivery.getMessage())) {
            body.append(delivery.getMessage()).append(System.lineSeparator()).append(System.lineSeparator());
        }

        if (delivery.getType() != null) {
            body.append("Type: ").append(delivery.getType()).append(System.lineSeparator());
        }
        if (delivery.getPriority() != null) {
            body.append("Priority: ").append(delivery.getPriority()).append(System.lineSeparator());
        }
        if (StringUtils.isNotBlank(delivery.getSourceService())) {
            body.append("Source service: ").append(delivery.getSourceService()).append(System.lineSeparator());
        }
        if (StringUtils.isNotBlank(delivery.getSourceEntityType())) {
            body.append("Source entity type: ").append(delivery.getSourceEntityType()).append(System.lineSeparator());
        }
        if (StringUtils.isNotBlank(delivery.getSourceEntityId())) {
            body.append("Source entity id: ").append(delivery.getSourceEntityId()).append(System.lineSeparator());
        }
        if (StringUtils.isNotBlank(delivery.getActionUrl())) {
            body.append(System.lineSeparator())
                    .append("Open: ")
                    .append(delivery.getActionUrl())
                    .append(System.lineSeparator());
        }

        if (body.isEmpty()) {
            return "You received a new FL-Net notification.";
        }

        return body.toString().trim();
    }
}

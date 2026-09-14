package bio.cosy.feddb.core.api.notification;

public interface NotificationDeliveryProvider {
    void deliver(NotificationBaseDTO delivery);
}

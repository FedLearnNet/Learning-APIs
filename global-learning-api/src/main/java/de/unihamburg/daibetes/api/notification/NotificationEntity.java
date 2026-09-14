package de.unihamburg.daibetes.api.notification;

import bio.cosy.feddb.core.api.notification.NotificationBaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "notifications")
@Getter
@Setter
public class NotificationEntity extends NotificationBaseEntity {
}

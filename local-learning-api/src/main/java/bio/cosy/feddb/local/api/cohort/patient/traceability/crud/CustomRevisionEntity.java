package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.envers.RevisionEntity;
import org.hibernate.envers.RevisionMapping;

@Entity
@Table(name = "revinfo",
        indexes = {
                @Index(name = "idx_revinfo_run_id_timestamp", columnList = "run_id, timestamp"),
                @Index(name = "idx_revinfo_connector_id", columnList = "connector_id")
        })
@RevisionEntity(UserRevisionListener.class)
@Getter
@Setter
public class CustomRevisionEntity extends RevisionMapping {
    // We use the RevissionMapping to inherit the default revision fields
    @Column(name = "keycloak_id", nullable = false, length = 1024)
    private String keycloakId;
    // For the websocket additions, we also save which import run did such changes
    @Column(name = "connector_id")
    private Long connectorId;
    @Column(name = "run_id")
    private Long runId;

    @Override
    public String toString() {
        return "CustomRevisionEntity{" +
                "id=" + getId() +
                ", timestamp=" + getTimestamp() +
                ", keycloakId='" + keycloakId + '\'' +
                ", connectorId='" + connectorId + '\'' +
                ", connectorRunId='" + runId + '\'' +
                '}';
    } // for prettyness
}

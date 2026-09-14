package bio.cosy.feddb.local.api.importer.run.preview.cache;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class ConnectorPreviewTransformationCacheAO
        implements PanacheRepository<ConnectorPreviewTransformationCacheEntity> {

    public Optional<ConnectorPreviewTransformationCacheEntity> find(
            Long connectorId, int stepIndex, String fingerprint) {
        return find("connector.id = ?1 and stepIndex = ?2 and fingerprint = ?3",
                connectorId, stepIndex, fingerprint).firstResultOptional();
    }

    public Optional<ConnectorPreviewTransformationCacheEntity> findStage(Long connectorId, int stepIndex) {
        return find("connector.id = ?1 and stepIndex = ?2", connectorId, stepIndex).firstResultOptional();
    }

    public long deleteFromStep(Long connectorId, int stepIndex) {
        return delete("connector.id = ?1 and stepIndex >= ?2", connectorId, stepIndex);
    }

    public long deleteForConnector(Long connectorId) {
        return delete("connector.id = ?1", connectorId);
    }
}

package bio.cosy.feddb.local.api.importer.connector;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ConnectorAO implements PanacheRepository<ConnectorEntity> {

    public List<ConnectorEntity> getAllByCohortId(Long cohortId) {
        return list("cohort.id", cohortId);
    }

    public void deleteAllByCohortId(Long cohortId) {
        delete("cohort.id", cohortId);
    }

    public Optional<ConnectorEntity> getAllByCohortId(Long cohortId, String name) {
        return find("cohort.id = ?1 and name = ?2", cohortId, name).firstResultOptional();
    }

    public List<ConnectorEntity> findByTriggerSourceConnectorId(Long sourceConnectorId) {
        return list("triggerSourceConnectorId", sourceConnectorId);
    }

    @SuppressWarnings("unchecked")
    public List<ConnectorEntity> findByInputFileId(Long fileId) {
        if (fileId == null) {
            return List.of();
        }

        return getEntityManager()
                .createNativeQuery("""
                        SELECT *
                        FROM app_connectors
                        WHERE input_config IS NOT NULL
                        AND input_config ->> 'fileId' = :fileId
                        """, ConnectorEntity.class)
                .setParameter("fileId", String.valueOf(fileId))
                .getResultList();
    }

    public List<ConnectorEntity> findAllWithSchedule() {
        return find("scheduleSettings is not null").list();
    }
}

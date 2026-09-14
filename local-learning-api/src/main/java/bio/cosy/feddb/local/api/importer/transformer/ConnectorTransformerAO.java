package bio.cosy.feddb.local.api.importer.transformer;


import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ConnectorTransformerAO implements PanacheRepository<ConnectorTransformerEntity> {

    public List<ConnectorTransformerEntity> getAllForConnector(Long connectorId) {
        return list("connector.id = ?1 order by position", connectorId);
    }

    public void deleteAllForConnector(Long connectorId) {
        delete("connector.id", connectorId);
    }

    public ConnectorTransformerEntity create(ConnectorTransformerEntity entity) {
        if (entity.getConnector() == null || entity.getConnector().getId() == null) {
            throw new IllegalArgumentException("Transformer connector must not be null");
        }

        if (entity.getPosition() == null) {
            Integer maxPosition = find(
                    "select max(t.position) from ConnectorTransformerEntity t where t.connector.id = ?1",
                    entity.getConnector().getId()
            ).project(Integer.class).firstResult();

            entity.setPosition((maxPosition == null ? 0 : maxPosition) + 1);
        } else {
            ConnectorTransformerEntity existingEntity = find(
                    "connector.id = ?1 and position = ?2",
                    entity.getConnector().getId(),
                    entity.getPosition()
            ).firstResult();

            if (existingEntity != null) {
                update(
                        "position = position + 1 where connector.id = ?1 and position >= ?2",
                        entity.getConnector().getId(),
                        entity.getPosition()
                );
            }
        }

        persist(entity);
        return entity;
    }
}

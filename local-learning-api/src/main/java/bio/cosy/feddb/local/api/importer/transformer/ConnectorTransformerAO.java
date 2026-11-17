package bio.cosy.feddb.local.api.importer.transformer;


import bio.cosy.feddb.local.api.importer.connector.ConnectorEntity;
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

    public List<ConnectorEntity> connectorsUsing(String appImage) {
        return getEntityManager().createQuery(
                        "select distinct t.connector from ConnectorTransformerEntity t where t.appImage = ?1", ConnectorEntity.class)
                .setParameter(1, appImage)
                .getResultList();
    }

    public void replaceAppImage(String previous, String appImage) {
        update("appImage = ?1 where appImage = ?2", appImage, previous);
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

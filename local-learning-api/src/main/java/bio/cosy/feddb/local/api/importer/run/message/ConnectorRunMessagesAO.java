package bio.cosy.feddb.local.api.importer.run.message;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ConnectorRunMessagesAO implements PanacheRepository<ConnectorRunMessagesEntity> {

    public List<ConnectorRunMessagesEntity> findByRun(Long id) {
        return list("run.id", Sort.by("createdAt", Sort.Direction.Ascending), id);
    }

    public int detachTransformer(Long transformerId) {
        return update("transformer = null where transformer.id = ?1", transformerId);
    }
}

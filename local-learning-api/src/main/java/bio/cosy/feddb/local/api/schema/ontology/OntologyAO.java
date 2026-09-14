package bio.cosy.feddb.local.api.schema.ontology;

import java.util.Optional;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class OntologyAO implements PanacheRepository<OntologyEntity> {
    // Only find by ID needed
    public Optional<OntologyEntity> findByGlobalId(String globalId) {
        // Find an ontology node by its global ID
        return find("globalId", globalId).firstResultOptional();
    }
}

package bio.cosy.feddb.local.api.schema.datatype;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class DataTypeAO implements PanacheRepository<DataTypeEntity> {
    // Only find by ID needed
    public Optional<DataTypeEntity> findByGlobalId(String globalId) {
        // Find a data type by its global ID
        return find("globalDataTypeId", globalId).firstResultOptional();
    }

}

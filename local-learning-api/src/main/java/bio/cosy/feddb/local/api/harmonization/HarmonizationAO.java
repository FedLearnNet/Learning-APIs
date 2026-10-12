package bio.cosy.feddb.local.api.harmonization;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class HarmonizationAO implements PanacheRepository<HarmonizationEntity> {

    public List<HarmonizationEntity> getAll(String keycloakId) {
        return list("keycloakId = ?1 order by id desc", keycloakId);
    }
}

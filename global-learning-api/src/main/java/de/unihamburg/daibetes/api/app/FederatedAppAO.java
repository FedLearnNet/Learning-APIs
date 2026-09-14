package de.unihamburg.daibetes.api.app;

import bio.cosy.feddb.core.api.app.FederatedAppType;
import bio.cosy.feddb.core.api.app.PublishStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class FederatedAppAO implements PanacheRepository<FederatedAppEntity> {



    //For distinct queries use normal JPQL
    public List<FederatedAppEntity> getAllPublished() {
        return find("select distinct f from FederatedAppEntity f " +
                "join f.versions v where v.versionPublishStatus = ?1 order by f.id desc", PublishStatus.PUBLISHED).list();
    }

    public Optional<FederatedAppEntity> findApp(Object id) {
        if (id instanceof Long) {
            return findByIdOptional((Long) id);
        } else if (id instanceof String) {
            return find("slug", id).firstResultOptional();
        }
        return Optional.empty();
    }

    public Optional<FederatedAppEntity> findByImageName(String name) {
        return find("imageName", name).firstResultOptional();
    }

    public Optional<FederatedAppEntity> findByUniqueAppId(UUID uniqueAppId) {
        return find("uniqueAppId", uniqueAppId).firstResultOptional();
    }

    public Optional<FederatedAppEntity> findBySlug(String slug) {
        return find("slug", slug).firstResultOptional();
    }


    public boolean existsById(Long id) {
        return count("id", id) > 0;
    }

    public Optional<FederatedAppType> findTypeById(Long id) {
        return findByIdOptional(id).map(FederatedAppEntity::getType);
    }

}

package de.unihamburg.daibetes.api.app.author;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class FederatedAppAuthorAO implements PanacheRepository<FederatedAppAuthorEntity> {


    public boolean isUserAuthor(String keycloakId, Long appId) {
        return this.find("federatedApp.id = ?1 and keycloakId = ?2", appId, keycloakId).count() > 0;
    }

    public List<FederatedAppAuthorEntity> getAllForUser(String keycloakId) {
        return this.find("keycloakId", keycloakId).list();
    }


    public List<FederatedAppAuthorEntity> getAllForApp(Long appId) {
        return this.find("federatedApp.id", appId).list();
    }

}

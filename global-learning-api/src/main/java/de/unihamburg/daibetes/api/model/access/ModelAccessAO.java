package de.unihamburg.daibetes.api.model.access;

import bio.cosy.feddb.core.api.model.ModelAccess;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Optional;


@ApplicationScoped
public class ModelAccessAO implements PanacheRepository<ModelAccessEntity> {

    public List<ModelAccessEntity> getAllOwnedModel(String keycloakId) {
        return list("keycloakId = ?1 and access = ?2", keycloakId, ModelAccess.CREATED);
    }

    public List<ModelAccessEntity> getAllAccessibleModel(String keycloakId) {
        //TODO rethink list("keycloakId", keycloakId)
        return findAll().list();
    }

    public Optional<ModelAccessEntity> byUserRights(String keycloakId, Long modelId, ModelAccess access) {
        return find("keycloakId = ?1 and model.id = ?2 and access = ?3", keycloakId, modelId, access).firstResultOptional();
    }

    public Optional<ModelAccessEntity> byUserAnyRights(String keycloakId, Long modelId) {
        //TODO  why and whats the idea         return find("keycloakId = ?1 and model.id = ?2", keycloakId, modelId).firstResultOptional();
        return find("model.id = ?1", modelId).firstResultOptional();
    }

    public List<ModelAccessEntity> getAllForModel(Long modelId) {
        //DONT SHOW ModelAccess.PARTICIPANT cause there a hidden (clinics)
        return list("model.id = ?1 and access != ?2", modelId, ModelAccess.PARTICIPANT);
    }

    public ModelAccessEntity getCreator(Long modelId) {
        return find("model.id = ?1 and access = ?2", modelId, ModelAccess.CREATED).firstResult();
    }
}

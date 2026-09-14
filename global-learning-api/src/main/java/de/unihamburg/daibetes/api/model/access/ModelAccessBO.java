package de.unihamburg.daibetes.api.model.access;

import bio.cosy.feddb.core.api.app.FederatedAppAuthorDTO;
import bio.cosy.feddb.core.api.model.ModelAccess;
import bio.cosy.feddb.core.api.model.ModelAccessDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.model.sub.ModelSubAO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;


@ApplicationScoped
public class ModelAccessBO extends BaseBo<ModelAccessDTO, ModelAccessEntity, ModelAccessAO, ModelAccessMapper> {

    @Inject
    FederatedAppAuthorBO authorBO;

    @Inject
    ModelSubAO subAO;

    public List<ModelAccessDTO> getAllForModel(Long modelId) {
        return mapper.entitiesToDtos(ao.getAllForModel(modelId));
    }

    public List<ModelAccessDTO> getAllOwnedModel(String keycloakId) {
        return mapper.entitiesToDtos(ao.getAllOwnedModel(keycloakId));
    }

    public boolean isUserOwner(String keycloakId, Long modelId) {
        return hasUserRights(keycloakId, modelId, ModelAccess.CREATED);
    }

    public boolean hasUserRights(String keycloakId, Long modelId, ModelAccess access) {
        return ao.byUserRights(keycloakId, modelId, access).isPresent();
    }

    public boolean hasUserAnyRights(String keycloakId, Long modelId) {
        return ao.byUserAnyRights(keycloakId, modelId).isPresent();
    }

    public boolean hasUserAnyRightsBySubId(String keycloakId, Long modelSubId) {
        Long modelId = subAO.findByIdOptional(modelSubId).orElseThrow(NotFoundException::new).getModelVersion().getModel().getId();
        return hasUserAnyRights(keycloakId, modelId);
    }

    public void create(Long appId, Long modelId) {
        List<FederatedAppAuthorDTO> authors = authorBO.getAllForApp(appId);
        for (FederatedAppAuthorDTO author : authors) {
            ModelAccessDTO access = new ModelAccessDTO();
            access.setKeycloakId(author.getKeycloakId());
            access.setAccess(ModelAccess.CREATED);
            access.setModelId(modelId);
            create(access);
        }
    }

    public ModelAccessDTO getCreator(Long modelId) {
        return mapper.entityToDto(ao.getCreator(modelId));
    }
}

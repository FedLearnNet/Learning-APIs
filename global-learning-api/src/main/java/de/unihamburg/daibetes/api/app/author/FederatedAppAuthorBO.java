package de.unihamburg.daibetes.api.app.author;

import bio.cosy.feddb.core.api.app.FederatedAppAuthorDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class FederatedAppAuthorBO extends BaseBo<FederatedAppAuthorDTO, FederatedAppAuthorEntity, FederatedAppAuthorAO, FederatedAppAuthorMapper> {

    @Inject
    FederatedAppVersionAO federatedAppVersionAO;

    public FederatedAppAuthorDTO create(Long appId, String keycloakId) {
        FederatedAppAuthorDTO authorDTO = new FederatedAppAuthorDTO();
        authorDTO.setFederatedAppId(appId);
        authorDTO.setKeycloakId(keycloakId);
        return super.create(authorDTO);
    }

    public FederatedAppAuthorDTO create(FederatedAppEntity app, String keycloakId) {
        FederatedAppAuthorEntity author = new FederatedAppAuthorEntity();
        author.setFederatedApp(app);
        author.setKeycloakId(keycloakId);
        ao.persist(author);
        return mapper.entityToDto(author);
    }

    public boolean isUserAuthor(String keycloakId, Long appId) {
        return ao.isUserAuthor(keycloakId, appId);
    }

    public boolean isUserAuthorVersionId(String keycloakId, Long appVersionId) {
        Long appId = federatedAppVersionAO.findByIdOptional(appVersionId).orElseThrow(NotFoundException::new).getFederatedApp().getId();
        return isUserAuthor(keycloakId, appId);
    }


    public List<FederatedAppAuthorDTO> getAllForApp(Long appId) {
        return mapper.entitiesToDtos(ao.getAllForApp(appId));
    }
}

package de.unihamburg.daibetes.api.app.tag;

import bio.cosy.feddb.core.api.app.FederatedAppTagDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FederatedAppTagBO extends BaseBo<FederatedAppTagDTO, FederatedAppTagEntity, FederatedAppTagAO, FederatedAppTagMapper> {


    public void createForApp(Set<FederatedAppTagDTO> tags, FederatedAppEntity app) {

        Set<FederatedAppTagEntity> addedTags = tags.stream()
                .map(this::getOrCreate)
                .peek(tag -> tag.getApps().add(app))
                .collect(Collectors.toSet());

        app.setTags(addedTags);
    }

    public FederatedAppTagEntity getOrCreate(FederatedAppTagDTO tag) {
        FederatedAppTagEntity foundTag = null;
        if (tag.getId() != null) {
            foundTag = this.ao.findById(tag.getId());
        }
        if (foundTag == null) {
            FederatedAppTagEntity entity = mapper.dtoToEntity(tag);
            ao.persist(entity);
            return entity;
        }
        return foundTag;
    }
}

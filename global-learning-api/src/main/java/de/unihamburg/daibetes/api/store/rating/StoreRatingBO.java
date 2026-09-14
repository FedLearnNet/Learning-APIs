package de.unihamburg.daibetes.api.store.rating;

import bio.cosy.feddb.core.api.store.StoreRatingDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.model.version.ModelVersionBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class StoreRatingBO extends BaseBo<StoreRatingDTO, StoreRatingEntity, StoreRatingRatingAO, StoreRatingRatingMapper> {

    @Inject
    FederatedAppBO federatedAppBO;

    @Inject
    ModelVersionBO modelVersionBO;

    public StoreRatingDTO addRatingApp(StoreRatingCreateDTO data, String keycloakId, Long appId) {
        federatedAppBO.checkExistsById(appId);

        StoreRatingEntity rating = ao.findByUserIdAndAppId(keycloakId, appId);
        if (rating != null) {
            rating.setRating(data.getRating());
            rating.setReviewText(data.getReviewText());
            ao.persist(rating);
            return mapper.entityToDto(rating);
        }
        StoreRatingDTO newRating = mapper.createDtoToEntity(data);
        newRating.setKeycloakId(keycloakId);
        newRating.setFederatedAppId(appId);
        newRating = create(newRating);
        return newRating;
    }

    public StoreRatingDTO addRatingModelVersion(StoreRatingCreateDTO data, String keycloakId, Long modelVersionId) {
        modelVersionBO.checkExistsById(modelVersionId);

        StoreRatingEntity rating = ao.findByUserIdAndModelVersionId(keycloakId, modelVersionId);
        if (rating != null) {
            rating.setRating(data.getRating());
            rating.setReviewText(data.getReviewText());
            ao.persist(rating);
            return mapper.entityToDto(rating);
        }
        StoreRatingDTO newRating = mapper.createDtoToEntity(data);
        newRating.setKeycloakId(keycloakId);
        newRating.setModelVersionId(modelVersionId);
        newRating = create(newRating);
        return newRating;
    }

    public List<StoreRatingDTO> getAllForApp(Long appId) {
        return mapper.entitiesToDtos(ao.findAllByAppId(appId).stream());
    }

    public List<StoreRatingDTO> getAllForModel(Long appId) {
        return mapper.entitiesToDtos(ao.findAllByAppId(appId).stream());
    }

}

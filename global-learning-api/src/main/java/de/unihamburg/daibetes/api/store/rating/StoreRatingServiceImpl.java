package de.unihamburg.daibetes.api.store.rating;

import bio.cosy.feddb.core.api.store.StoreRatingDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class StoreRatingServiceImpl implements StoreRatingRatingService {
    @Inject
    StoreRatingBO bo;

    @Inject
    UserIdentity userIdentity;

    @Override
    @Transactional
    public StoreRatingDTO createForApp(Long appId, StoreRatingCreateDTO createDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.addRatingApp(createDTO, keycloakId, appId);
    }

    @Override
    @Transactional
    public StoreRatingDTO createForModel(Long modelId, StoreRatingCreateDTO createDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.addRatingModelVersion(createDTO, keycloakId, modelId);
    }

    @Override
    public List<StoreRatingDTO> listForApp(Long appId) {
        return bo.getAllForApp(appId);
    }

    @Override
    public List<StoreRatingDTO> listForModel(Long modelVersionId) {
        return bo.getAllForModel(modelVersionId);
    }
}

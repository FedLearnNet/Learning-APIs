package de.unihamburg.daibetes.api.app.validation;

import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.base.BaseValidationResultDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ToolInputValidationServiceImpl implements ToolInputValidationService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    ToolValidationBO bo;

    @Override
    public BaseValidationResultDTO validateHyperParamValues(ToolHyperParamConfigValidateRequestDTO request) {
        return bo.validateHyperParamValues(request.getValue(), request.getConfig());
    }

    @Override
    @Transactional
    public BaseValidationResultDTO validateFile(Long fileId, ToolConfigDTO config) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.validateFile(fileId, config, keycloakId);
    }
}

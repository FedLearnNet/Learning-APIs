package de.unihamburg.daibetes.api.app.validation;

import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.api.app.config.ToolHyperParamConfigDTO;
import bio.cosy.feddb.core.api.app.config.validation.HyperParamValidator;
import bio.cosy.feddb.core.api.app.config.validation.ToolInAndOutputValidator;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.base.BaseValidationResultDTO;
import de.unihamburg.daibetes.api.file.FileBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ToolValidationBO extends ToolInAndOutputValidator {

    @Inject
    FileBO fileBO;

    public BaseValidationResultDTO validateHyperParamValues(Object rawValue, ToolHyperParamConfigDTO config) {
        return HyperParamValidator.validateInputValues(rawValue, config);
    }

    public BaseValidationResultDTO validateFile(Long fileId, ToolConfigDTO config, String keycloakId) {
        FileDTO file = fileBO.getById(fileId, keycloakId);
        return evaluate(config, file);
    }

    @Override
    public FileProfile getFileProfile(FileDTO file) {
        return fileBO.getFileStatisticsBySecret(file.getId(), file.getSecret());
    }

    @Override
    public String getFileContent(FileDTO file) {
        return fileBO.getFileContentBySecret(file.getId(), file.getSecret()).getContent();
    }
}

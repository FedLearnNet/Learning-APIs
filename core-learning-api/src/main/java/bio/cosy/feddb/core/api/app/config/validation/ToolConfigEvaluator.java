package bio.cosy.feddb.core.api.app.config.validation;

import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.base.BaseValidationResultDTO;

public interface ToolConfigEvaluator {
    BaseValidationResultDTO evaluateProfile(ToolConfigDTO config, FileDTO file, FileProfile profile);
    BaseValidationResultDTO evaluateContent(ToolConfigDTO config, FileDTO file, String content);

}

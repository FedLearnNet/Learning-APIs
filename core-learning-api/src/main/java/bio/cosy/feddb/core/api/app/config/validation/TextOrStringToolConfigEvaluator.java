package bio.cosy.feddb.core.api.app.config.validation;

import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.base.BaseValidationResultDTO;

import java.util.List;

public class TextOrStringToolConfigEvaluator implements ToolConfigEvaluator {

    @Override
    public BaseValidationResultDTO evaluateContent(ToolConfigDTO config, FileDTO file, String content) {
        try {
            readTextValue(content);
            return BaseValidationResultDTO.ok();
        } catch (Exception e) {
            return BaseValidationResultDTO.fail(List.of(message(e)));
        }
    }

    private void readTextValue(String v) {
        if (v == null) {
            throw new IllegalArgumentException("Expected text content, got: null");
        }
    }

    private String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    @Override
    public BaseValidationResultDTO evaluateProfile(ToolConfigDTO config, FileDTO file, FileProfile profile) {
        return null;
    }
}

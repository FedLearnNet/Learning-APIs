package bio.cosy.feddb.core.api.app.config.validation;

import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.base.BaseValidationResultDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public class JsonToolConfigEvaluator implements ToolConfigEvaluator {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public BaseValidationResultDTO evaluateProfile(ToolConfigDTO config, FileDTO file, FileProfile profile) {
        return null;
    }

    @Override
    public BaseValidationResultDTO evaluateContent(ToolConfigDTO config, FileDTO file, String content) {
        try {
            readJsonValue(content);
            return BaseValidationResultDTO.ok();
        } catch (Exception e) {
            return BaseValidationResultDTO.fail(List.of(message(e)));
        }
    }

    private JsonNode readJsonValue(String v) throws Exception {
        if (v == null) {
            throw new IllegalArgumentException("Expected JSON string, got: null");
        }
        String trimmed = v.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("JSON is empty.");
        }
        return objectMapper.readTree(trimmed);
    }

    private String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

}

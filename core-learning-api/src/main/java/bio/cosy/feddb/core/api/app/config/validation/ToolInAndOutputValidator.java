package bio.cosy.feddb.core.api.app.config.validation;

import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.base.BaseValidationResultDTO;

import java.util.Map;

public abstract class ToolInAndOutputValidator implements ToolConfigEvaluator {

    private final Map<ToolConfigDataType, ToolConfigEvaluator> evaluatorMap;

    public ToolInAndOutputValidator() {
        this.evaluatorMap = Map.of(
                ToolConfigDataType.TSV, new TableConfigEvaluator(),
                ToolConfigDataType.CSV, new TableConfigEvaluator(),
                ToolConfigDataType.JSON, new JsonToolConfigEvaluator(),
                ToolConfigDataType.TEXT, new TextOrStringToolConfigEvaluator(),
                ToolConfigDataType.STRING, new TextOrStringToolConfigEvaluator(),
                ToolConfigDataType.IMAGE, new ImageToolConfigEvaluator(),
                ToolConfigDataType.HTML, new ImageToolConfigEvaluator()
        );
    }

    public BaseValidationResultDTO evaluate(ToolConfigDTO config, FileDTO file) {
        if (config == null || config.getType() == null) {
            return BaseValidationResultDTO.fail("ToolConfigDTO or its dataType is null.");
        }
        ToolConfigEvaluator evaluator = evaluatorMap.get(config.getType());
        if (evaluator == null) {
            return null;
        }
        if (config.getType().equals(ToolConfigDataType.TSV) || config.getType().equals(ToolConfigDataType.CSV)) {
            FileProfile profile = getFileProfile(file);
            return evaluator.evaluateProfile(config, file, profile);
        }
        String content = getFileContent(file);
        return evaluator.evaluateContent(config, file, content);
    }

    @Override
    public BaseValidationResultDTO evaluateProfile(ToolConfigDTO config, FileDTO file, FileProfile profile) {
        return evaluatorMap.get(ToolConfigDataType.CSV).evaluateProfile(config, file, profile);
    }

    @Override
    public BaseValidationResultDTO evaluateContent(ToolConfigDTO config, FileDTO file, String content) {
        ToolConfigEvaluator evaluator = evaluatorMap.get(config.getType());
        if (evaluator == null) {
            return null;
        }
        return evaluator.evaluateContent(config, file, content);
    }

    public abstract FileProfile getFileProfile(FileDTO file);

    public abstract String getFileContent(FileDTO file);

}

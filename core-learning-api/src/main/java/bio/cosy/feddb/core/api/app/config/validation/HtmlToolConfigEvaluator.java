package bio.cosy.feddb.core.api.app.config.validation;

import bio.cosy.feddb.core.api.app.config.ToolConfigDTO;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.base.BaseValidationResultDTO;

import java.util.List;

public class HtmlToolConfigEvaluator implements ToolConfigEvaluator {

    @Override
    public BaseValidationResultDTO evaluateContent(ToolConfigDTO config, FileDTO file, String content) {
        try {
            String html = readTextValue(content);

            if (html.trim().isEmpty()) {
                return BaseValidationResultDTO.fail(List.of("HTML is empty."));
            }

            String lower = lstrip(html).toLowerCase();
            if (!lower.contains("<html") && !lower.contains("<!doctype html")) {
                BaseValidationResultDTO ok = BaseValidationResultDTO.ok();
                ok.getMeta().put("warnings", List.of("HTML does not appear to contain <html> or <!doctype html>."));
                return ok;
            }

            return BaseValidationResultDTO.ok();
        } catch (Exception e) {
            return BaseValidationResultDTO.fail(List.of(message(e)));
        }
    }

    private String readTextValue(String v) {
        if (v == null) {
            throw new IllegalArgumentException("Expected text content, got: null");
        }
        return v;
    }

    private String lstrip(String s) {
        int i = 0;
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return s.substring(i);
    }

    private String message(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    @Override
    public BaseValidationResultDTO evaluateProfile(ToolConfigDTO config, FileDTO file, FileProfile profile) {
        return null;
    }
}

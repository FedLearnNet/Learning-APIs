package de.unihamburg.daibetes.api.app.config;

import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedAppConfigNameMapper {

    default String sanitizeVariableName(String name) {
        if(StringUtils.isEmpty(name)){
            return name;
        }
        // Convert camelCase or PascalCase to snake_case
        String snakeCase = name.replaceAll("([a-z])([A-Z]+)", "$1_$2").toLowerCase();
        // Replace invalid characters with underscores
        String sanitized = snakeCase.replaceAll("\\.", "__");
        sanitized = sanitized.replaceAll("[^0-9a-zA-Z_]", "_");
        // Variable names must not start with a digit
        if(StringUtils.isEmpty(sanitized)){
            return sanitized;
        }
        if (Character.isDigit(sanitized.charAt(0))) {
            sanitized = "_" + sanitized;
        }
        // Ensure the name is not a Python keyword
        if (isPythonKeyword(sanitized)) {
            sanitized = sanitized + "_var";
        }
        return sanitized;
    }

    default boolean isPythonKeyword(String name) {
        // List of Python keywords
        String[] keywords = {
                "False", "class", "finally", "is", "return", "None", "continue", "for",
                "lambda", "try", "True", "def", "from", "nonlocal", "while", "and",
                "del", "global", "not", "with", "as", "elif", "if", "or", "yield",
                "assert", "else", "import", "pass", "break", "except", "in", "raise"
        };
        for (String keyword : keywords) {
            if (keyword.equals(name)) {
                return true;
            }
        }
        return false;
    }

    default String getFileName(String variableName, String type) {
        return variableName + "." + type.toLowerCase();
    }
}

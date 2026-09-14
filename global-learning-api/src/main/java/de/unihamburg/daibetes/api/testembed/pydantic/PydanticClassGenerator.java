package de.unihamburg.daibetes.api.testembed.pydantic;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.config.ToolConfigsDTO;
import bio.cosy.feddb.core.api.app.config.ToolHyperParamConfigDTO;
import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.api.app.config.ToolOutputConfigDTO;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigNameMapper;
import org.mapstruct.factory.Mappers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PydanticClassGenerator {
    private static final FederatedAppConfigNameMapper nameMapper = Mappers.getMapper(FederatedAppConfigNameMapper.class);
    // Map Java types to Python types
    private static final Map<String, String> typeMapping = new HashMap<>();

    static {
        // HyperParam types
        typeMapping.put("STRING", "str");
        typeMapping.put("INTEGER", "int");
        typeMapping.put("FLOAT", "float");
        typeMapping.put("BOOLEAN", "bool");
        typeMapping.put("CATEGORICAL", "str");
        // Input types
        typeMapping.put("CSV", "str"); // Assuming file paths or content as strings
        typeMapping.put("TSV", "str"); // Assuming file paths or content as strings
        typeMapping.put("JSON", "dict");
        typeMapping.put("MIXED", "Any");
        typeMapping.put("IMAGE", "Any"); // Could be bytes or specific image types
        typeMapping.put("TEXT", "str");
        typeMapping.put("AUDIO", "Any");
        typeMapping.put("TIME_SERIES", "Any");
        typeMapping.put("SPARSE", "Any");
        typeMapping.put("TENSOR", "Any");
        // Output types
        typeMapping.put("HTML", "str");
    }

    public static String generateBasicStructure(String className, String foundation) {
        return "from pydantic.dataclasses import dataclass\n" +
                "from typing import Any\n" +
                "from config.config import " + foundation + "\n\n\n" +
                "@dataclass\nclass " + className + "(" + foundation + "):\n";
    }

    public static PydanticUpdateDTO generatePydanticClasses(ToolConfigsDTO config) {
        PydanticUpdateDTO pydanticUpdateDTO = new PydanticUpdateDTO();
        pydanticUpdateDTO.hyperparam = generatePydanticHyperParam(config.getHyperparams());
        pydanticUpdateDTO.input = generatePydanticInput(config.getInput());
        pydanticUpdateDTO.output = generatePydanticOutput(config.getOutput());
        return pydanticUpdateDTO;
    }

    public static PydanticUpdateDTO generatePydanticClasses(FederatedAppDetailDTO app) {
        return generatePydanticClasses(app.getAppConfig());
    }

    public static String generatePydanticHyperParam(List<ToolHyperParamConfigDTO> hyperparams) {
        StringBuilder classesContent = new StringBuilder(generateBasicStructure("MyAppConfig", "AppConfig"));
        if (hyperparams == null || hyperparams.isEmpty()) {
            classesContent.append("    pass\n");
            return classesContent.toString();
        }
        for (ToolHyperParamConfigDTO param : hyperparams) {
            String varName = nameMapper.sanitizeVariableName(param.getName());
            String varType = mapType(param.getType().toString());
            String defaultValue = param.getDefaultValue();

            if (defaultValue == null || defaultValue.isEmpty()) {
                varType = "Any";
                defaultValue = "None";
            } else {
                defaultValue = formatDefaultValue(varType, defaultValue);
            }

            classesContent.append("    ").append(varName).append(": ")
                    .append(varType).append(" = ").append(defaultValue).append("\n");
        }
        return classesContent.toString();
    }

    public static String generatePydanticInput(List<ToolInputConfigDTO> inputs) {
        StringBuilder classesContent = new StringBuilder(generateBasicStructure("MyAppInputConfig", "AppInputConfig"));
        if (inputs == null || inputs.isEmpty()) {
            classesContent.append("    pass\n");
            return classesContent.toString();
        }
        for (ToolInputConfigDTO param : inputs) {
            String varName = nameMapper.sanitizeVariableName(param.getName());
            String varType = mapType(param.getType().toString());

            classesContent.append("    ").append(varName).append(": ").append(varType).append(" = None\n");
        }
        return classesContent.toString();
    }

    public static String generatePydanticOutput(List<ToolOutputConfigDTO> outputs) {
        StringBuilder classesContent = new StringBuilder(generateBasicStructure("MyAppOutputConfig", "AppOutputConfig"));
        if (outputs == null || outputs.isEmpty()) {
            classesContent.append("    pass\n");
            return classesContent.toString();
        }
        for (ToolOutputConfigDTO param : outputs) {
            String varName = nameMapper.sanitizeVariableName(param.getName());
            String varType = mapType(param.getType().toString());

            classesContent.append("    ").append(varName).append(": ").append(varType).append(" = None\n");
        }
        return classesContent.toString();
    }


    private static String mapType(String javaType) {
        if (javaType == null) {
            return "Any";
        }
        return typeMapping.getOrDefault(javaType.toUpperCase(), "Any");
    }

    private static String formatDefaultValue(String varType, String defaultValue) {
        // Add quotes around strings
        if ("str".equals(varType) || "Any".equals(varType)) {
            return "\"" + defaultValue + "\"";
        }
        // For booleans, ensure true/false are in lowercase
        if ("bool".equals(varType)) {
            return defaultValue.toLowerCase();
        }
        // For other types, return as is
        return defaultValue;
    }
}

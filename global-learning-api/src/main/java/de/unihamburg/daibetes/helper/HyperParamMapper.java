package de.unihamburg.daibetes.helper;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.mapstruct.Mapper;
import tools.jackson.core.JacksonException;

import java.util.LinkedHashMap;

@Mapper(config = QuarkusMappingConfig.class)
public interface HyperParamMapper {

    ObjectMapper objectMapper = new JsonMapper();


    default LinkedHashMap<String, Object> jsonStringToHyperparamsAllowError(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (JacksonException e) {
            return new LinkedHashMap<>();
        }
    }

    default LinkedHashMap<String, String> jsonStringToInputPathsAllowError(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class));
        } catch (JacksonException e) {
            return new LinkedHashMap<>();
        }
    }

    default LinkedHashMap<String, Object> jsonStringToHyperparams(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (JacksonException e) {
            throw new RuntimeException("Error parsing JSON string to LinkedHashMap", e);
        }
    }

    default String hyperparamsToJsonString(LinkedHashMap<String, Object> hyperparams) {
        if (hyperparams == null || hyperparams.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(hyperparams);
        } catch (JacksonException e) {
            throw new RuntimeException("Error converting LinkedHashMap to JSON string", e);
        }
    }

    default String hyperparamsToJsonStringAllowError(LinkedHashMap<String, Object> hyperparams) {
        if (hyperparams == null || hyperparams.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(hyperparams);
        } catch (JacksonException e) {
            return "";
        }
    }

    default String inputPathsToJsonStringAllowError(LinkedHashMap<String, String> hyperparams) {
        if (hyperparams == null || hyperparams.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(hyperparams);
        } catch (JacksonException e) {
            return "";
        }
    }
}

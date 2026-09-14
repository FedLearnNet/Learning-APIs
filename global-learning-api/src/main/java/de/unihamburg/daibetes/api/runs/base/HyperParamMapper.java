package de.unihamburg.daibetes.api.runs.base;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;

import java.io.IOException;
import java.util.LinkedHashMap;

@Mapper(config = QuarkusMappingConfig.class)
public interface HyperParamMapper {

    ObjectMapper objectMapper = new ObjectMapper();


    default LinkedHashMap<String, Object> jsonStringToHyperparamsAllowError(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    default LinkedHashMap<String, String> jsonStringToInputPathsAllowError(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, String.class));
        } catch (IOException e) {
            return new LinkedHashMap<>();
        }
    }

    default LinkedHashMap<String, Object> jsonStringToHyperparams(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
        } catch (IOException e) {
            throw new RuntimeException("Error parsing JSON string to LinkedHashMap", e);
        }
    }

    default String hyperparamsToJsonString(LinkedHashMap<String, Object> hyperparams) {
        if (hyperparams == null || hyperparams.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(hyperparams);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting LinkedHashMap to JSON string", e);
        }
    }

    default String hyperparamsToJsonStringAllowError(LinkedHashMap<String, Object> hyperparams) {
        if (hyperparams == null || hyperparams.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(hyperparams);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    default String inputPathsToJsonStringAllowError(LinkedHashMap<String, String> hyperparams) {
        if (hyperparams == null || hyperparams.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(hyperparams);
        } catch (JsonProcessingException e) {
            return "";
        }
    }
}

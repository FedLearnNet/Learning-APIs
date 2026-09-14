package de.unihamburg.daibetes.api.app.config.hyperparam;

import bio.cosy.feddb.core.api.app.config.ToolHyperParamConfigDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.app.config.FederatedAppConfigNameMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedAppHyperParamConfigMapper extends BaseMapper<ToolHyperParamConfigDTO, FederatedAppHyperParamConfigEntity> {

    ObjectMapper objectMapper = new ObjectMapper();
    FederatedAppConfigNameMapper nameMapper = Mappers.getMapper(FederatedAppConfigNameMapper.class);

    @Mapping(target = "federatedAppVersion", ignore = true)
    @Mapping(target = "options", source = "options", qualifiedByName = "optionsToJsonString")
    FederatedAppHyperParamConfigEntity dtoToEntity(ToolHyperParamConfigDTO dto);

    @Mapping(target = "variableName", expression = "java(nameMapper.sanitizeVariableName(entity.getName()))")
    @Mapping(target = "options", source = "options", qualifiedByName = "jsonStringToOptions")
    ToolHyperParamConfigDTO entityToDto(FederatedAppHyperParamConfigEntity entity);


    @Named("jsonStringToOptions")
    default List<String> jsonStringToOptions(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructCollectionLikeType(List.class, String.class));
        } catch (IOException e) {
            throw new RuntimeException("Error parsing JSON string to Options", e);
        }
    }

    @Named("optionsToJsonString")
    default String optionsToJsonString(List<String> options) {
        if (options == null || options.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(options);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting Options to JSON string", e);
        }
    }
}

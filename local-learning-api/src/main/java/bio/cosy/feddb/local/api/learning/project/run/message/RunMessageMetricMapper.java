package bio.cosy.feddb.local.api.learning.project.run.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageMetricDTO;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Optional;

@Mapper(config = QuarkusMappingConfig.class)
public interface RunMessageMetricMapper {

    ObjectMapper objectMapper = new ObjectMapper();

    @Mapping(target = "metric", source = "message", qualifiedByName = "mapMessageToMetric")
    @Mapping(target = "value", source = "message", qualifiedByName = "mapMessageToValue")
    @Mapping(target = "x", expression = "java(extractX(dto))")
    @Mapping(target = "XUnit", source = "message", qualifiedByName = "mapMessageToXUnit")
    RunMessageMetricDTO dtoToMetricDTO(RunMessageDTO dto);

    @Mapping(target = "message", expression = "java(mapFieldsToJson(dto))")
    RunMessageDTO logDtoToDto(RunMessageMetricDTO dto);


    List<RunMessageMetricDTO> dtosToMetricDtos(List<RunMessageDTO> dtos);


    @Named("mapMessageToMetric")
    default String mapMessageToMetric(String message) {
        return extractJsonField(message, "metric");
    }

    @Named("mapMessageToValue")
    default String mapMessageToValue(String message) {
        return extractJsonField(message, "value");
    }

    @Named("mapMessageToXUnit")
    default String mapMessageToXUnit(String message) {
        return Optional.ofNullable(extractJsonField(message, "xUnit", true)).orElse("time");
    }


    default String extractJsonField(String json, String fieldName) {
        return extractJsonField(json, fieldName, false);
    }

    default String extractJsonField(String json, String fieldName, boolean nullable) {
        try {
            String value = objectMapper.readTree(json).path(fieldName).asText();
            if (nullable) {
                if (StringUtils.isEmpty(value)) {
                    return null;
                }
            }
            return value;
        } catch (Exception e) {
            return null;
        }
    }

    default String extractX(RunMessageDTO dto) {
        if (dto.getMessage() == null) {
            return String.valueOf(dto.getCreatedAt().getTime());
        }
        return Optional.ofNullable(extractJsonField(dto.getMessage(), "x", true))
                .orElse(String.valueOf(dto.getCreatedAt().getTime()));
    }

    default String mapFieldsToJson(RunMessageMetricDTO dto) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("metric", dto.getMetric());
            node.put("value", dto.getValue());
            node.put("x", dto.getX());
            node.put("XUnit", dto.getXUnit());
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting fields to JSON string", e);
        }
    }

}

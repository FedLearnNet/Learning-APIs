package bio.cosy.feddb.local.api.learning.project.run.message;

import bio.cosy.feddb.core.api.run.message.RunMessageDTO;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(config = QuarkusMappingConfig.class)
public interface RunMessageLogMapper {

    ObjectMapper objectMapper = new ObjectMapper();

    @Mapping(target = "workerId", ignore = true)
    @Mapping(target = "severity", source = "message", qualifiedByName = "mapMessageToSeverity")
    @Mapping(target = "message", source = "message", qualifiedByName = "mapMessageToMessage")
    @Mapping(target = "caller", source = "message", qualifiedByName = "mapMessageToCaller")
    @Mapping(target = "group", source = "message", qualifiedByName = "mapMessageToGroup")
    @Mapping(target = "stackTrace", source = "message", qualifiedByName = "mapMessageToStackTrace")
    RunMessageLogDTO dtoToLogDTO(RunMessageDTO dto);

    @Mapping(target = "message", expression = "java(mapFieldsToJson(dto))")
    RunMessageDTO logDtoToDto(RunMessageLogDTO dto);

    List<RunMessageLogDTO> dtosToLogDtos(List<RunMessageDTO> dtos);

    @Named("mapMessageToSeverity")
    default String mapMessageToSeverity(String message) {
        return extractJsonField(message, "severity");
    }

    @Named("mapMessageToMessage")
    default String mapMessageToMessage(String message) {
        return extractJsonField(message, "message");
    }


    @Named("mapMessageToCaller")
    default String mapMessageToCaller(String message) {
        return extractJsonField(message, "caller");
    }

    @Named("mapMessageToGroup")
    default String mapMessageToGroup(String message) {
        return extractJsonField(message, "group");
    }

    @Named("mapMessageToStackTrace")
    default String mapMessageToStackTrace(String message) {
        return extractJsonField(message, "stackTrace");
    }

    default String extractJsonField(String json, String fieldName) {
        try {
            return objectMapper.readTree(json).path(fieldName).asText();

        } catch (Exception e) {
            return null;
        }
    }

    default String mapFieldsToJson(RunMessageLogDTO dto) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("severity", dto.getSeverity());
            node.put("message", dto.getMessage());
            node.put("caller", dto.getCaller());
            node.put("group", dto.getGroup());
            node.put("stackTrace", dto.getStackTrace());
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting fields to JSON string", e);
        }
    }
}

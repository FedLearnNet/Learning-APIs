package bio.cosy.feddb.core.api.run.message;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.BaseMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;

public abstract class BaseRunMessageBO<Entity extends BaseRunMessageEntity, Ao extends PanacheRepository<Entity>,
        Mapper extends BaseMapper<RunMessageDTO, Entity>> extends BaseBo<RunMessageDTO, Entity, Ao, Mapper> {

    private final ObjectMapper objectMapper = new ObjectMapper();


    public RunMessageMetricDTO create(RunMessageMetricDTO dto, Long runId) {
        if (runId != null) {
            dto.setRunId(runId);
            return create(dto);
        }
        return dto;
    }

    public RunMessageLogDTO create(RunMessageLogDTO dto, Long runId) {
        if (runId != null) {
            dto.setRunId(runId);
            return create(dto);
        }
        return dto;
    }

    public RunMessageMetricDTO create(RunMessageMetricDTO dto) {
        RunMessageDTO toCreate = logDtoToDto(dto);
        RunMessageDTO created = create(toCreate);
        return dtoToMetricDTO(created);

    }

    public RunMessageLogDTO create(RunMessageLogDTO dto) {
        RunMessageDTO toCreate = logDtoToDto(dto);
        RunMessageDTO created = create(toCreate);
        return dtoToLogDTO(created);
    }

    public RunMessageLogDTO createLog(String dto, Long runId) {
        RunMessageLogDTO log = new RunMessageLogDTO();
        log.setMessage(dto);
        log.setType(RunMessageTypes.LOG);
        return create(log, runId);
    }


    public List<RunMessageMetricDTO> mapToMetrics(List<RunMessageDTO> dtos) {
        return dtos.stream().map(this::dtoToMetricDTO).toList();
    }

    public List<RunMessageLogDTO> mapToLogs(List<RunMessageDTO> dtos) {
        return dtos.stream().map(this::dtoToLogDTO).toList();
    }


    public RunMessageLogDTO dtoToLogDTO(RunMessageDTO dto) {
        RunMessageLogDTO metricDTO = new RunMessageLogDTO();
        metricDTO.setId(dto.getId());
        metricDTO.setRunId(dto.getRunId());
        metricDTO.setType(dto.getType());
        metricDTO.setCreatedAt(dto.getCreatedAt());
        metricDTO.setUpdatedAt(dto.getUpdatedAt());
        String message = dto.getMessage();
        metricDTO.setSeverity(extractJsonField(message, "severity"));
        metricDTO.setMessage(extractJsonField(message, "message"));
        metricDTO.setCaller(extractJsonField(message, "caller"));
        metricDTO.setGroup(extractJsonField(message, "group"));
        metricDTO.setStackTrace(extractJsonField(message, "stackTrace"));
        return metricDTO;
    }

    public RunMessageDTO logDtoToDto(RunMessageLogDTO dto) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("severity", dto.getSeverity());
            node.put("message", dto.getMessage());
            node.put("caller", dto.getCaller());
            node.put("group", dto.getGroup());
            node.put("stackTrace", dto.getStackTrace());
            dto.setMessage(objectMapper.writeValueAsString(node));
            return dto;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting fields to JSON string", e);
        }
    }

    public RunMessageMetricDTO dtoToMetricDTO(RunMessageDTO dto) {
        RunMessageMetricDTO metricDTO = new RunMessageMetricDTO();
        metricDTO.setId(dto.getId());
        metricDTO.setRunId(dto.getRunId());
        metricDTO.setType(dto.getType());
        metricDTO.setCreatedAt(dto.getCreatedAt());
        metricDTO.setUpdatedAt(dto.getUpdatedAt());
        String message = dto.getMessage();
        metricDTO.setMetric(extractJsonField(message, "metric"));
        metricDTO.setValue(extractJsonField(message, "value"));
        metricDTO.setX(extractX(dto));
        metricDTO.setXUnit(Optional.ofNullable(extractJsonField(message, "xUnit", true)).orElse("time"));
        return metricDTO;
    }

    public RunMessageDTO logDtoToDto(RunMessageMetricDTO dto) {
        try {
            ObjectNode node = objectMapper.createObjectNode();
            node.put("metric", dto.getMetric());
            node.put("value", dto.getValue());
            node.put("x", dto.getX());
            node.put("XUnit", dto.getXUnit());
            dto.setMessage(objectMapper.writeValueAsString(node));
            return dto;
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Error converting fields to JSON string", e);
        }
    }


    private String extractJsonField(String json, String fieldName) {
        return extractJsonField(json, fieldName, false);
    }

    private String extractJsonField(String json, String fieldName, boolean nullable) {
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

    private String extractX(RunMessageDTO dto) {
        if (dto.getMessage() == null) {
            return String.valueOf(dto.getCreatedAt().getTime());
        }
        return Optional.ofNullable(extractJsonField(dto.getMessage(), "x", true))
                .orElse(String.valueOf(dto.getCreatedAt().getTime()));
    }

}

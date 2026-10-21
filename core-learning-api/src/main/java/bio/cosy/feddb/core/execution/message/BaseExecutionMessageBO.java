package bio.cosy.feddb.core.execution.message;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.BaseMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepository;

import java.util.List;

public abstract class BaseExecutionMessageBO<Entity extends BaseExecutionMessageEntity, Ao extends PanacheRepository<Entity>,
        Mapper extends BaseMapper<ExecutionMessageDTO, Entity>> extends BaseBo<ExecutionMessageDTO, Entity, Ao, Mapper> {

    public ExecutionMessageMetricDTO create(ExecutionMessageMetricDTO dto, Long runId) {
        if (runId != null) {
            dto.setRunId(runId);
            return create(dto);
        }
        return dto;
    }

    public ExecutionMessageLogDTO create(ExecutionMessageLogDTO dto, Long runId) {
        if (runId != null) {
            dto.setRunId(runId);
            return create(dto);
        }
        return dto;
    }

    public ExecutionMessageMetricDTO create(ExecutionMessageMetricDTO dto) {
        ExecutionMessageDTO created = super.create(dto);
        return (ExecutionMessageMetricDTO) created;
    }

    public ExecutionMessageLogDTO create(ExecutionMessageLogDTO dto) {
        ExecutionMessageDTO created = super.create(dto);
        return (ExecutionMessageLogDTO) created;
    }


    public List<ExecutionMessageMetricDTO> mapToMetrics(List<ExecutionMessageDTO> dtos) {
        return dtos.stream().map(m -> (ExecutionMessageMetricDTO) m).toList();
    }

    public List<ExecutionMessageLogDTO> mapToLogs(List<ExecutionMessageDTO> dtos) {
        return dtos.stream().map(l -> (ExecutionMessageLogDTO) l).toList();
    }

}

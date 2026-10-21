package bio.cosy.feddb.local.api.cohort.patient.tools.log;

import bio.cosy.feddb.core.execution.message.ExecutionMessageDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface PatientToolRunLogMapper extends BaseMapper<ExecutionMessageDTO, PatientToolRunLogEntity> {

    @Mappings({
            @Mapping(target = "workerId", ignore = true),
            @Mapping(target = "runId", source = "run.id")
    })
    ExecutionMessageDTO entityToDto(PatientToolRunLogEntity entity);

    @Mapping(target = "run.id", source = "runId")
    PatientToolRunLogEntity dtoToEntity(ExecutionMessageDTO sourceCode);
}

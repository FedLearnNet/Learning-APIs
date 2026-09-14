package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

import java.util.List;

@Mapper(config = QuarkusMappingConfig.class)
public interface PatientToolRunMapper extends BaseMapper<PatientToolRunDTO, PatientToolRunEntity> {


    @Mappings({
            @Mapping(target = "cohortId", source = "cohort.id")
    })
    PatientToolRunDTO entityToDto(PatientToolRunEntity entity);


    @Mappings({
            @Mapping(target = "cohort", ignore = true)
    })
    PatientToolRunEntity dtoToEntity(PatientToolRunDTO dto);

    @Mapping(target = "runId", source = "id")
    PatientToolRunStatusDTO entityToStatusDto(PatientToolRunEntity entity);

    @Mapping(target = "id", source = "run.id")
    @Mapping(target = "outputFileName", source = "outputs.fileName")
    @Mapping(target = "outputSize", source = "outputs.size")
    @Mapping(target = "downloadReady", expression = "java(outputs != null)")
    PatientToolRunSummaryDTO toSummaryDto(PatientToolRunEntity run, PatientToolRunFileEntity outputs);

    @Mapping(target = "runId", source = "runId")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "logs", source = "logs")
    @Mapping(target = "outputs", source = "outputs")
    @Mapping(target = "downloadReady", source = "downloadReady")
    PatientToolRunProgressDTO toProgressDto(Long runId,
                                            PatientToolRunStatusDTO status,
                                            List<RunMessageLogDTO> logs,
                                            List<PatientToolRunOutputDTO> outputs,
                                            boolean downloadReady);


}

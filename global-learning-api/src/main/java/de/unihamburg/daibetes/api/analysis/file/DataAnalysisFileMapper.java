package de.unihamburg.daibetes.api.analysis.file;

import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.file.FileMapper;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;


@Mapper(config = QuarkusMappingConfig.class)
public interface DataAnalysisFileMapper extends BaseMapper<DataAnalysisFileDTO, DataAnalysisFileEntity> {
    FileMapper fileMapper = Mappers.getMapper(FileMapper.class);

    @Mappings({
            @Mapping(target = "dataAnalysisId", source = "dataAnalysis.id"),
            @Mapping(target = "file", expression = "java(fileMapper.entityToDto(entity.getFile()))"),
            @Mapping(target = "attemptId", source = "attempt.id", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE),
    })
    DataAnalysisFileDTO entityToDto(DataAnalysisFileEntity entity);

    @Mappings({
            @Mapping(target = "dataAnalysis.id", source = "dataAnalysisId"),
            @Mapping(target = "file", expression = "java(fileMapper.dtoToEntity(dto.getFile()))"),
            @Mapping(target = "attempt", source = "attemptId", qualifiedByName = "mapAttempt")
    })
    DataAnalysisFileEntity dtoToEntity(DataAnalysisFileDTO dto);

    @Named("mapAttempt")
    default ExecutionAttemptEntity mapAttempt(Long id) {
        if (id == null) {
            return null;
        }
        ExecutionAttemptEntity attempt = new ExecutionAttemptEntity();
        attempt.setId(id);
        return attempt;
    }
}

package de.unihamburg.daibetes.api.analysis.file;

import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionEntity;
import de.unihamburg.daibetes.api.file.FileMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;


@Mapper(config = QuarkusMappingConfig.class)
public interface DataAnalysisFileMapper extends BaseMapper<DataAnalysisFileDTO, DataAnalysisFileEntity> {
    FileMapper fileMapper = Mappers.getMapper(FileMapper.class);

    @Mappings({
            @Mapping(target = "predictionId", source = "prediction.id", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE),
            @Mapping(target = "dataAnalysisId", source = "dataAnalysis.id"),
            @Mapping(target = "file", expression = "java(fileMapper.entityToDto(entity.getFile()))"),
            @Mapping(target = "workflowStepId", source = "workflowStep.id", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE),
    })
    DataAnalysisFileDTO entityToDto(DataAnalysisFileEntity entity);

    @Mappings({
            @Mapping(target = "dataAnalysis.id", source = "dataAnalysisId"),
            @Mapping(target = "prediction", source = "predictionId", qualifiedByName = "mapPrediction"),
            @Mapping(target = "file", expression = "java(fileMapper.dtoToEntity(dto.getFile()))"),
            @Mapping(target = "workflowStep.id", source = "workflowStepId")
    })
    DataAnalysisFileEntity dtoToEntity(DataAnalysisFileDTO dto);


    @Named("mapPrediction")
    default DataAnalysisPredictionEntity mapPrediction(Long id) {
        if (id == null) {
            return null;
        }
        DataAnalysisPredictionEntity prediction = new DataAnalysisPredictionEntity();
        prediction.setId(id);
        return prediction;
    }
}

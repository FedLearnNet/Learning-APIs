package de.unihamburg.daibetes.api.analysis.prediction;

import bio.cosy.feddb.core.api.model.prediction.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisPredictionDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileMapper;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.api.runs.base.HyperParamMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.util.LinkedHashMap;


@Mapper(config = QuarkusMappingConfig.class)
public interface DataAnalysisPredictionMapper extends BaseMapper<DataAnalysisPredictionDTO, DataAnalysisPredictionEntity> {
    ObjectMapper objectMapper = new ObjectMapper();

    HyperParamMapper hyperParamMapper = Mappers.getMapper(HyperParamMapper.class);
    DataAnalysisFileMapper fileMapper = Mappers.getMapper(DataAnalysisFileMapper.class);

    @Mappings({
            @Mapping(target = "modelSubId", source = "subModel", qualifiedByName = "getModelVersionId"),
            @Mapping(target = "modelId", source = "subModel", qualifiedByName = "getModelId"),
            @Mapping(target = "name", source = "entity", qualifiedByName = "getName"),
            @Mapping(target = "inputs", expression = "java(hyperParamMapper.jsonStringToHyperparamsAllowError(entity.getInputs()))"),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.jsonStringToHyperparamsAllowError(entity.getHyperParams()))"),
            @Mapping(target = "result", expression = "java(addFilePredictions(entity))"),
            @Mapping(target = "outputFiles", expression = "java(fileMapper.entitiesToDtos(entity.getOutputFiles()))"),
            @Mapping(target = "inputFiles", expression = "java(fileMapper.entitiesToDtos(entity.getInputFiles()))"),
            @Mapping(target = "modelVersionId", source = "subModel.modelVersion.id"),
            @Mapping(target = "dataAnalysisId", source = "dataAnalysis.id"),
            @Mapping(target = "appVersionId", source = "federatedAppVersion", qualifiedByName = "getAppVersionId"),
            @Mapping(target = "imageName", ignore = true),
            @Mapping(target = "workflowId", ignore = true)
    })
    DataAnalysisPredictionDTO entityToDto(DataAnalysisPredictionEntity entity);

    @Mappings({
            @Mapping(target = "subModel", source = "modelSubId", qualifiedByName = "getModelVersion"),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.hyperparamsToJsonStringAllowError(dto.getHyperParams()))"),
            @Mapping(target = "inputs", expression = "java(hyperParamMapper.hyperparamsToJsonStringAllowError(dto.getInputs()))"),
            @Mapping(target = "result", expression = "java(hyperParamMapper.hyperparamsToJsonStringAllowError(dto.getResult()))"),
            @Mapping(target = "dataAnalysis", ignore = true),
            @Mapping(target = "files", ignore = true),
            @Mapping(target = "inputFiles", ignore = true),
            @Mapping(target = "outputFiles", ignore = true),
            @Mapping(target = "federatedAppVersion", source = "appVersionId", qualifiedByName = "getAppVersion"),
            @Mapping(target = "messages", ignore = true)
    })
    DataAnalysisPredictionEntity dtoToEntity(DataAnalysisPredictionDTO dto);

    @Mappings({
            @Mapping(target = "lastError", ignore = true),
            @Mapping(target = "result", ignore = true),
            @Mapping(target = "status", source = "id", qualifiedByName = "getDefaultStatus"),
            @Mapping(target = "subModel", source = "modelSubId", qualifiedByName = "getModelVersion"),
            @Mapping(target = "containerId", ignore = true),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.hyperparamsToJsonStringAllowError(dto.getHyperParams()))"),
            @Mapping(target = "inputs", expression = "java(hyperParamMapper.hyperparamsToJsonStringAllowError(dto.getInputs()))"),
            @Mapping(target = "lastLog", ignore = true),
            @Mapping(target = "dataAnalysis", ignore = true),
            @Mapping(target = "files", ignore = true),
            @Mapping(target = "inputFiles", ignore = true),
            @Mapping(target = "outputFiles", ignore = true),
            @Mapping(target = "federatedAppVersion", source = "appVersionId", qualifiedByName = "getAppVersion"),
            @Mapping(target = "rawLog", ignore = true),
            @Mapping(target = "messages", ignore = true)
    })
    DataAnalysisPredictionEntity createDtoToEntity(DataAnalysisCreatePredictionDTO dto);

    @Mappings({
            @Mapping(target = "lastError", ignore = true),
            @Mapping(target = "result", ignore = true),
            @Mapping(target = "status", source = "dto.id", qualifiedByName = "getDefaultStatus"),
            @Mapping(target = "subModel", source = "dto.modelSubId", qualifiedByName = "getModelVersion"),
            @Mapping(target = "containerId", ignore = true),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.hyperparamsToJsonStringAllowError(dto.getHyperParams()))"),
            @Mapping(target = "inputs", expression = "java(hyperParamMapper.hyperparamsToJsonStringAllowError(dto.getInputs()))"),
            @Mapping(target = "lastLog", ignore = true),
            @Mapping(target = "dataAnalysis.id", source = "workflowId"),
            @Mapping(target = "keycloakId", source = "keycloakId"),
            @Mapping(target = "files", ignore = true),
            @Mapping(target = "inputFiles", ignore = true),
            @Mapping(target = "outputFiles", ignore = true),
            @Mapping(target = "federatedAppVersion", source = "dto.appVersionId", qualifiedByName = "getAppVersion"),
            @Mapping(target = "rawLog", ignore = true),
            @Mapping(target = "messages", ignore = true)
    })
    DataAnalysisPredictionEntity createDtoToEntityWorkflow(DataAnalysisCreatePredictionDTO dto, Long workflowId, String keycloakId);


    @Named("getDefaultStatus")
    default RunStatusTypes getDefaultStatus(Long id) {
        return RunStatusTypes.PENDING;
    }

    @Named("getName")
    default String getName(DataAnalysisPredictionEntity entity) {
        if (entity == null) {
            return null;
        }
        if (entity.getSubModel() != null && entity.getSubModel().getModelVersion() != null
                && entity.getSubModel().getModelVersion().getModel() != null) {
            return entity.getSubModel().getModelVersion().getModel().getName();
        }
        if (entity.getFederatedAppVersion() != null && entity.getFederatedAppVersion().getFederatedApp() != null) {
            return entity.getFederatedAppVersion().getFederatedApp().getName();
        }
        return null;
    }

    @Named("getAppVersionId")
    default Long getAppVersionId(FederatedAppVersionEntity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getId();
    }

    @Named("getModelVersionId")
    default Long getModelVersionId(ModelSubEntity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getId();
    }

    @Named("getModelId")
    default Long getModelId(ModelSubEntity entity) {
        if (entity == null) {
            return null;
        }
        return entity.getModelVersion().getModel().getId();
    }


    @Named("getAppVersion")
    default FederatedAppVersionEntity getAppVersion(Long id) {
        if (id == null) {
            return null;
        }
        FederatedAppVersionEntity entity = new FederatedAppVersionEntity();
        entity.setId(id);
        return entity;
    }

    @Named("getModelVersion")
    default ModelSubEntity getModelVersion(Long id) {
        if (id == null) {
            return null;
        }
        ModelSubEntity entity = new ModelSubEntity();
        entity.setId(id);
        return entity;
    }

    default LinkedHashMap<String, Object> addFilePredictions(DataAnalysisPredictionEntity entity) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        String jsonString = entity.getResult();
        if (!(jsonString == null || jsonString.isEmpty())) {
            try {
                result = objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class));
            } catch (IOException e) {
                return result;
            }
        }
        return result;
    }
}

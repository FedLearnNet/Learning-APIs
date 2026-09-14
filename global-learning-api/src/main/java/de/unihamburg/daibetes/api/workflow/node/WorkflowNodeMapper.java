package de.unihamburg.daibetes.api.workflow.node;


import bio.cosy.feddb.core.api.app.FederatedAppDTO;
import bio.cosy.feddb.core.api.app.FederatedAppType;
import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowPositionDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.app.FederatedAppMapper;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.ModelMapper;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.api.runs.base.HyperParamMapper;
import de.unihamburg.daibetes.api.workflow.export.WorkflowNodeExportDTO;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.io.IOException;

@Mapper(config = QuarkusMappingConfig.class)
public interface WorkflowNodeMapper extends BaseMapper<WorkflowNodeDetailDTO, WorkflowNodeEntity> {

    HyperParamMapper hyperParamMapper = Mappers.getMapper(HyperParamMapper.class);
    FederatedAppMapper appMapper = Mappers.getMapper(FederatedAppMapper.class);
    ModelMapper modelMapper = Mappers.getMapper(ModelMapper.class);
    ObjectMapper objectMapper = new ObjectMapper();

    @Mappings({
            @Mapping(target = "workflow.id", source = "workflowId"),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.hyperparamsToJsonString(dto.getHyperParams()))"),
            @Mapping(target = "position", expression = "java(positionToJsonString(dto.getPosition()))"),
            @Mapping(target = "federatedAppVersion", source = "federatedAppVersionId", qualifiedByName = "getAppVersion"),
            @Mapping(target = "subModel", source = "modelSubId", qualifiedByName = "getModelVersion"),
            @Mapping(target = "inComingConnections", ignore = true),
            @Mapping(target = "outComingConnections", ignore = true)
    })
    WorkflowNodeEntity dtoToEntity(WorkflowNodeDetailDTO dto);

    @Mappings({
            @Mapping(target = "workflow", ignore = true),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.hyperparamsToJsonString(dto.getHyperParams()))"),
            @Mapping(target = "position", expression = "java(positionToJsonString(dto.getPosition()))"),
            @Mapping(target = "federatedAppVersion", ignore = true),
            @Mapping(target = "subModel", ignore = true),
            @Mapping(target = "inComingConnections", ignore = true),
            @Mapping(target = "outComingConnections", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true)
    })
    WorkflowNodeEntity exportDtoToEntity(WorkflowNodeExportDTO dto);


    @Mappings({
            @Mapping(target = "workflowId", source = "workflow.id"),
            @Mapping(target = "appVersion", ignore = true),
            @Mapping(target = "federatedAppId", ignore = true),
            @Mapping(target = "federatedAppVersionId", ignore = true),
            @Mapping(target = "hasChildren", ignore = true),
            @Mapping(target = "hasParent", ignore = true),
            @Mapping(target = "imageName", ignore = true),
            @Mapping(target = "oldFCVersion", ignore = true),
            @Mapping(target = "hyperParams", expression = "java(hyperParamMapper.jsonStringToHyperparams(entity.getHyperParams()))"),
            @Mapping(target = "modelSubId", ignore = true),
            @Mapping(target = "appDetail", ignore = true),
            @Mapping(target = "modelDetail", ignore = true),
            @Mapping(target = "position", expression = "java(jsonStringToPosition(entity.getPosition()))"),
            @Mapping(target = "appInputConfig", ignore = true),
            @Mapping(target = "supportsFederatedLearning", ignore = true),
            @Mapping(target = "isTrainable", ignore = true)
    })
    WorkflowNodeDetailDTO entityToDto(WorkflowNodeEntity entity);

    default void afterMapping(WorkflowNodeEntity entity, WorkflowNodeDTO dto) {
        if (entity.getSubModel() != null) {
            dto.setModelSubId(entity.getSubModel().getId());
            FederatedAppVersionEntity appVersion = entity.getSubModel().getModelVersion().getModel().getFederatedAppVersion();
            setAppVersion(dto, appVersion);
        }
        if (entity.getFederatedAppVersion() != null) {
            setAppVersion(dto, entity.getFederatedAppVersion());
        }
        if (entity.getInComingConnections() != null) {
            dto.setHasParent(!entity.getInComingConnections().isEmpty());
        }
        if (entity.getOutComingConnections() != null) {
            dto.setHasChildren(!entity.getOutComingConnections().isEmpty());
        }
    }

    @AfterMapping
    default void afterDetailMapping(WorkflowNodeEntity entity, @MappingTarget WorkflowNodeDetailDTO dto) {
        afterMapping(entity, dto);
        if (entity.getSubModel() != null) {
            ModelDTO model = modelMapper.entityToDto(entity.getSubModel().getModelVersion().getModel());
            FederatedAppDTO app = appMapper.entityToDto(entity.getSubModel().getModelVersion().getModel().getFederatedAppVersion().getFederatedApp());
            dto.setAppDetail(appMapper.dtoToDetailDto(app));
            dto.setModelDetail(modelMapper.toDetail(model));
        }
        if (entity.getFederatedAppVersion() != null) {
            FederatedAppDTO app = appMapper.entityToDto(entity.getFederatedAppVersion().getFederatedApp());
            dto.setAppDetail(appMapper.dtoToDetailDto(app));
        }
    }

    default void setAppVersion(WorkflowNodeDTO dto, FederatedAppVersionEntity appVersion) {
        dto.setFederatedAppVersionId(appVersion.getId());
        dto.setAppVersion(appVersion.getVersionFormatted());
        dto.setFederatedAppId(appVersion.getFederatedApp().getId());
        dto.setImageName(appVersion.getImageName());
        dto.setOldFCVersion(appVersion.getFederatedApp().getOldFCVersion());
        dto.setSupportsFederatedLearning(appVersion.getFederatedApp().isSupportsFederatedLearning());
        dto.setIsTrainable(appVersion.getFederatedApp().getType().equals(FederatedAppType.ANALYSIS));
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


    default WorkflowPositionDTO jsonStringToPosition(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return new WorkflowPositionDTO(0, 0);
        }
        try {
            return objectMapper.readValue(jsonString, WorkflowPositionDTO.class);
        } catch (IOException e) {
            return new WorkflowPositionDTO(0, 0);
        }
    }


    default String positionToJsonString(WorkflowPositionDTO position) {
        if (position == null) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(position);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

}

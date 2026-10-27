package de.unihamburg.daibetes.execution.experiment;

import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface ExecutionExperimentMapper {

    @Mappings({
            @Mapping(target = "federatedAppId", source = "federatedAppVersion.federatedApp.id"),
            @Mapping(target = "federatedAppVersionId", source = "federatedAppVersion.id"),
            @Mapping(target = "federatedAppVersionName", source = "federatedAppVersion.versionFormatted"),
            @Mapping(target = "projectId", source = "project.id"),
            @Mapping(target = "workflowId", source = "workflow.id")
    })
    ExecutionExperimentDTO entityToDto(ExecutionExperimentEntity entity);
}

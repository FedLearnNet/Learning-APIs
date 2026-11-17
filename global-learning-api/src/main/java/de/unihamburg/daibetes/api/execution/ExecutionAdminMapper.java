package de.unihamburg.daibetes.api.execution;

import bio.cosy.feddb.core.api.execution.ExecutionRunSummaryDTO;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface ExecutionAdminMapper {

    @Mappings({
            @Mapping(target = "stepCount", expression = "java(run.getSteps().size())"),
            @Mapping(target = "attemptCount", expression = "java(run.getSteps().stream().mapToInt(step -> step.getAttempts().size()).sum())")
    })
    ExecutionRunSummaryDTO mapRunEntityToSummaryDTO(ExecutionRunEntity run);
}

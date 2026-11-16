package de.unihamburg.daibetes.api.analysis.run;

import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptDTO;
import de.unihamburg.daibetes.execution.run.ExecutionRunDTO;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

/** Widens the execution DTOs to the data analysis ones; steps and what the app produced are added by {@link DataAnalysisRunResultBO}. */
@Mapper(config = QuarkusMappingConfig.class)
public interface DataAnalysisRunMapper {

    @Mappings({
            @Mapping(target = "steps", ignore = true),
            @Mapping(target = "dataAnalysisId", ignore = true),
            @Mapping(target = "workflowId", ignore = true)
    })
    DataAnalysisRunDTO toRun(ExecutionRunDTO run);

    @Mappings({
            @Mapping(target = "outputData", ignore = true),
            @Mapping(target = "lastLog", ignore = true),
            @Mapping(target = "logs", ignore = true),
            @Mapping(target = "metrics", ignore = true),
            @Mapping(target = "inputFiles", ignore = true),
            @Mapping(target = "outputFiles", ignore = true)
    })
    DataAnalysisAttemptDTO toAttempt(ExecutionAttemptDTO attempt);
}

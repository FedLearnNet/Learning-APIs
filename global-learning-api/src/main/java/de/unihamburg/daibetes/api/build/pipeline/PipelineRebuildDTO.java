package de.unihamburg.daibetes.api.build.pipeline;

import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class PipelineRebuildDTO extends PipelineCreateDTO {
    boolean existsInStore;
}

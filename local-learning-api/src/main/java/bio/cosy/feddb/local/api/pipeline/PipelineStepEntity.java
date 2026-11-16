package bio.cosy.feddb.local.api.pipeline;

import bio.cosy.feddb.core.api.pipeline.BasePipelineStepEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "pipeline_steps")
public class PipelineStepEntity extends BasePipelineStepEntity<PipelineEntity> {
}

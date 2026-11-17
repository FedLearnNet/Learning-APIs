package de.unihamburg.daibetes.api.build.pipeline.steps;

import bio.cosy.feddb.core.api.pipeline.BasePipelineStepEntity;
import de.unihamburg.daibetes.api.build.pipeline.PipelineEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "pipeline_steps")
public class PipelineStepEntity extends BasePipelineStepEntity<PipelineEntity> {
}

package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.pipeline.BasePipelineEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.build.pipeline.steps.PipelineStepEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@Entity
@Table(name = "pipeline")
public class PipelineEntity extends BasePipelineEntity<PipelineStepEntity> {

    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_type")
    private PipelineType pipelineType;

    @OneToMany(mappedBy = "pipeline", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("createdAt ASC")
    private List<PipelineStepEntity> steps = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "model_sub_id")
    private ModelSubEntity modelSub;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_version_id")
    private FederatedAppVersionEntity appVersion;

}

package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.pipeline.BasePipelineEntity;
import bio.cosy.feddb.core.api.pipeline.PipelineRunMode;
import de.unihamburg.daibetes.api.app.version.FederatedAppPublishDTO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.build.pipeline.steps.PipelineStepEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "run_mode", length = 32, nullable = false)
    private PipelineRunMode runMode = PipelineRunMode.BUILD_ONLY;

    // publish settings of an ALL run
    @Column(name = "publish_request", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private FederatedAppPublishDTO publishRequest;

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

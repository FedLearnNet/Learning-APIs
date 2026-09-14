package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.core.base.BaseEntity;
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
public class PipelineEntity extends BaseEntity {


    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_status")
    private PipelineStatus pipelineStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_type")
    private PipelineType pipelineType;

    private String secret;

    @Column(length = 8, name = "docker_tag")
    private String dockerTag;

    @Column(name = "container_id", nullable = true)
    private String containerId;

    @Column(name = "publish_info", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private AppPublishInfoDTO publishInfo;

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

package de.unihamburg.daibetes.api.model.sub;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.api.model.ModelSubStatus;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionEntity;
import de.unihamburg.daibetes.api.build.pipeline.PipelineEntity;
import de.unihamburg.daibetes.api.model.sub.file.ModelSubFileEntity;
import de.unihamburg.daibetes.api.model.version.ModelVersionEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.runs.experiment.run.ExperimentRunEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "model_subs")
public class ModelSubEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    private ModelSubStatus status;

    @Column(name = "image_name")
    private String imageName;

    @Column(name = "publish_info", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private AppPublishInfoDTO publishInfo;

    @Column(name = "publish_hash", length = 100, unique = true)
    private String publishHash;

    @Column(name = "model_name", length = 255)
    private String modelName;

    @Column(name = "model_path", length = 255)
    private String modelPath;

    //TRAINING
    /*@ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_training_id")
    private ExperimentRunEntity federatedExperimentRun; */

    @OneToOne
    @JoinColumn(name = "experiment_run_id")
    private ExperimentRunEntity experimentRun;

    @ManyToOne
    @JoinColumn(name = "federated_experiment_id")
    private ProjectFederatedExperimentEntity federatedExperiment;

    @ManyToOne
    @JoinColumn(name = "model_version_id", nullable = false)
    private ModelVersionEntity modelVersion;

    @OneToMany(mappedBy = "modelSub")
    @OrderBy("id ASC")
    private Set<PipelineEntity> pipelines;

    @OneToMany(mappedBy = "subModel")
    private Set<DataAnalysisPredictionEntity> predictions;

    @OneToMany(mappedBy = "modelSub")
    private Set<ModelSubFileEntity> files;
}

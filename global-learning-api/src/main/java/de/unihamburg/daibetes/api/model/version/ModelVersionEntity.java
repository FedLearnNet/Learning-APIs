package de.unihamburg.daibetes.api.model.version;

import de.unihamburg.daibetes.api.model.ModelEntity;
import bio.cosy.feddb.core.api.model.ModelPublishStatus;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.execution.experiment.ExecutionExperimentEntity;
import bio.cosy.feddb.core.base.BaseStoreVersionEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "model_versions")
public class ModelVersionEntity extends BaseStoreVersionEntity {

    @Column(name = "publish_status", length = 50)
    @Enumerated(EnumType.STRING)
    private ModelPublishStatus publishStatus = ModelPublishStatus.PRIVATE;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "model_id", nullable = false)
    private ModelEntity model;


    //TRAINING
    /*@ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_app_version_id")
    private FederatedAppVersionEntity appVersion; */

    //TRAINING
    /*@ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_training_id")
    private ExecutionExperimentEntity federatedExperiment; */

    // The experiment whose trials trained it.
    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "experiment_id")
    private ExecutionExperimentEntity experiment;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_experiment_id")
    private ExecutionExperimentEntity federatedExperiment;

    @OneToMany(mappedBy = "modelVersion", cascade = CascadeType.ALL)
    private Set<ModelSubEntity> subModels;

}

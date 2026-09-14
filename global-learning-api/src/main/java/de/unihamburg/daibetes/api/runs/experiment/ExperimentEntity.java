package de.unihamburg.daibetes.api.runs.experiment;

import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.ModelEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.api.model.version.ModelVersionEntity;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import de.unihamburg.daibetes.api.runs.experiment.run.ExperimentRunEntity;
import de.unihamburg.daibetes.api.runs.test.message.TestRunMessageEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "experiments")
public class ExperimentEntity extends BaseEntity {

    private String name;
    private String description;

    @Column(columnDefinition="TEXT")
    private String input;

    @Column(name = "input_file_path")
    private String inputFilePath;

    @Column(columnDefinition="TEXT", name = "diagram_config")
    private String diagramConfig;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "federated_app_version_id", nullable = false)
    private FederatedAppVersionEntity federatedAppVersion;

    @OneToMany(mappedBy = "experiment", cascade = { CascadeType.MERGE, CascadeType.REFRESH })
    private Set<ExperimentRunEntity> runs;

    @OneToMany(mappedBy = "experiment", cascade = CascadeType.ALL)
    private Set<ModelVersionEntity> modelVersion;
}

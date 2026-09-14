package de.unihamburg.daibetes.api.analysis.prediction;

import bio.cosy.feddb.core.api.run.RunMetaDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseAuthEntity;
import de.unihamburg.daibetes.api.analysis.DataAnalysisEntity;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.message.DataAnalysisWorkflowRunMessagesEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import org.hibernate.type.SqlTypes;

import java.util.Set;
import java.util.stream.Collectors;

@Setter
@Getter
@Entity
@Table(name = "data_analysis_predictions")
public class DataAnalysisPredictionEntity extends BaseAuthEntity {

    @Enumerated(EnumType.STRING)
    private RunStatusTypes status;

    @Column(name = "last_log", columnDefinition = "TEXT")
    private String lastLog;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "raw_logs", columnDefinition = "TEXT")
    private String rawLog;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(columnDefinition = "TEXT")
    private String inputs;

    @Column(name = "hyper_params", columnDefinition = "TEXT")
    private String hyperParams;

    @Column(name = "container_id")
    private String containerId;

    // Run metadata (timings + reserved fields) as JSON metadata, nullable for historical runs.
    // Extensible without a schema change. RUNTIME always stored; OVERHEAD_* only when
    // posymed.runtime.overhead.enabled is true.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
    private RunMetaDTO meta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sub_model_id", nullable = true)
    private ModelSubEntity subModel;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_version_id", nullable = true)
    private FederatedAppVersionEntity federatedAppVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_analysis_id", nullable = true)
    private DataAnalysisEntity dataAnalysis;

    @OneToMany(mappedBy = "prediction")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<DataAnalysisFileEntity> files;

    @OneToMany(mappedBy = "prediction", cascade = {CascadeType.MERGE, CascadeType.REFRESH})
    @OrderBy("id ASC")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<DataAnalysisWorkflowRunMessagesEntity> messages;


    public void setInputFiles(Set<DataAnalysisFileEntity> inputFiles) {
        if (files == null) {
            files = inputFiles;
        } else {
            files.addAll(inputFiles);
        }
    }

    public void setOutputFiles(Set<DataAnalysisFileEntity> outputFiles) {
        if (files == null) {
            files = outputFiles;
        } else {
            files.addAll(outputFiles);
        }
    }

    public Set<DataAnalysisFileEntity> getInputFiles() {
        if (files == null) {
            return Set.of();
        }
        return files.stream()
                .filter(file -> file.getInputName() != null)
                .collect(Collectors.toSet());
    }

    public Set<DataAnalysisFileEntity> getOutputFiles() {
        if (files == null) {
            return Set.of();
        }
        return files.stream()
                .filter(file -> file.getOutputName() != null)
                .collect(Collectors.toSet());
    }
}

package de.unihamburg.daibetes.api.analysis.file;

import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.base.BaseAuthEntity;
import de.unihamburg.daibetes.api.analysis.DataAnalysisEntity;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepEntity;
import de.unihamburg.daibetes.api.file.FileEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Setter
@Getter
@Entity
@Table(name = "data_analysis_files")
public class DataAnalysisFileEntity extends BaseAuthEntity {

    //Optional association to a prediction
    @Column(name = "output_name")
    public String outputName;

    @Column(name = "input_name")
    public String inputName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "file_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private FileEntity file;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "data_analysis_id", nullable = false)
    private DataAnalysisEntity dataAnalysis;

    //Optional association to a prediction
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prediction_id")
    private DataAnalysisPredictionEntity prediction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workflow_step_id")
    private DataAnalysisWorkflowRunStepEntity workflowStep;

}

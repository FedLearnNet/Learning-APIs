package de.unihamburg.daibetes.api.analysis.worklfow.message;

import bio.cosy.feddb.core.api.run.message.BaseRunMessageEntity;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionEntity;
import bio.cosy.feddb.core.api.model.prediction.DataAnalysisRunModesEnum;
import de.unihamburg.daibetes.api.analysis.worklfow.step.DataAnalysisWorkflowRunStepEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "data_analysis_workflow_run_step_messages")
public class DataAnalysisWorkflowRunMessagesEntity extends BaseRunMessageEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "run_mode", nullable = false)
    private DataAnalysisRunModesEnum runMode;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "experiment_step_id")
    private DataAnalysisWorkflowRunStepEntity step;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "prediction_id")
    private DataAnalysisPredictionEntity prediction;
}

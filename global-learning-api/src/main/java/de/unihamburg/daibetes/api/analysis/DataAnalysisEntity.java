package de.unihamburg.daibetes.api.analysis;

import bio.cosy.feddb.core.base.BaseAuthEntity;
import de.unihamburg.daibetes.api.analysis.chat.DataAnalysisChatMessageEntity;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileEntity;
import de.unihamburg.daibetes.api.analysis.prediction.DataAnalysisPredictionEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.DataAnalysisWorkflowRunEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "data_analysis")
public class DataAnalysisEntity extends BaseAuthEntity {

    @Column(columnDefinition = "TEXT")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String llmSummary;

    @OneToMany(mappedBy = "dataAnalysis")
    @OrderBy("id ASC")
    private Set<DataAnalysisPredictionEntity> predictions;

    @OneToMany(mappedBy = "dataAnalysis")
    @OrderBy("id ASC")
    private Set<DataAnalysisWorkflowRunEntity> workflowPredictions;

    @OneToMany(mappedBy = "dataAnalysis")
    @OrderBy("id ASC")
    private Set<DataAnalysisFileEntity> files;

    @OneToMany(mappedBy = "dataAnalysis")
    @OrderBy("id ASC")
    private Set<DataAnalysisChatMessageEntity> chatMessages;

}

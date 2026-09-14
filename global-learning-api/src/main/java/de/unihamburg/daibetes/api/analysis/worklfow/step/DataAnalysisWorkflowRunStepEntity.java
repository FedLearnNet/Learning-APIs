package de.unihamburg.daibetes.api.analysis.worklfow.step;

import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepEntity;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.DataAnalysisWorkflowRunEntity;
import de.unihamburg.daibetes.api.analysis.worklfow.message.DataAnalysisWorkflowRunMessagesEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Set;
import java.util.stream.Collectors;

@Setter
@Getter
@Entity
@Table(name = "data_analysis_workflow_run_steps")
public class DataAnalysisWorkflowRunStepEntity extends BaseWorkflowStepEntity<WorkflowNodeEntity, DataAnalysisWorkflowRunEntity> {

    @OneToMany(mappedBy = "step", cascade = {CascadeType.MERGE, CascadeType.REFRESH})
    @OrderBy("id ASC")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<DataAnalysisWorkflowRunMessagesEntity> messages;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(columnDefinition = "TEXT")
    private String inputs;

    @OneToMany(mappedBy = "workflowStep")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<DataAnalysisFileEntity> files;


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

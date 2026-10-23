package de.unihamburg.daibetes.api.analysis.file;

import bio.cosy.feddb.core.api.app.config.ToolInputConfigDTO;
import bio.cosy.feddb.core.base.BaseAuthEntity;
import de.unihamburg.daibetes.api.analysis.DataAnalysisEntity;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
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

    // Optional association to the execution attempt that read or wrote the file
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id")
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ExecutionAttemptEntity attempt;

}

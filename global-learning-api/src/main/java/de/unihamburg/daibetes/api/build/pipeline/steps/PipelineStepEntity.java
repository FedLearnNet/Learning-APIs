package de.unihamburg.daibetes.api.build.pipeline.steps;

import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.build.pipeline.PipelineEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Setter
@Getter
@Entity
@Table(name = "pipeline_steps")
public class PipelineStepEntity extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StepName name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PipelineStatus stepStatus;

    @Column(columnDefinition = "TEXT")
    private String logs;

    @Column(name = "progress")
    private Integer progress;

    @Column(name = "started_at")
    private Date startedAt;

    @Column(name = "finished_at")
    private Date finishedAt;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pipeline_id", nullable = false)
    private PipelineEntity pipeline;

    public void appendLog(String logMessage) {
        if (this.logs == null) {
            this.logs = logMessage;
        } else {
            this.logs += "\n" + logMessage;
        }
    }

}

package bio.cosy.feddb.core.api.pipeline;

import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;
import java.util.Set;

@Setter
@Getter
@MappedSuperclass
public class BasePipelineStepEntity<P extends BasePipelineEntity<?>> extends BaseEntity {

    private static final Set<PipelineStatus> FINISHED = Set.of(PipelineStatus.SUCCESS, PipelineStatus.WARNING, PipelineStatus.FAILED);

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
    private P pipeline;

    public void appendLog(String logMessage) {
        logs = logs == null ? logMessage : logs + "\n" + logMessage;
    }

    public void apply(PipelineStatusUpdateDTO dto) {
        stepStatus = dto.getStatus();
        if (dto.getLogs() != null && !dto.getLogs().isBlank()) {
            appendLog(dto.getLogs());
        }
        if (dto.getProgress() != null) {
            progress = dto.getProgress();
        }
        if (startedAt == null) {
            startedAt = new Date();
        }
        if (dto.getStatus() == PipelineStatus.FAILED && dto.getErrorCode() != null) {
            errorCode = dto.getErrorCode();
            errorMessage = dto.getErrorMessage();
        }
        if (FINISHED.contains(dto.getStatus())) {
            finishedAt = new Date();
        }
    }
}

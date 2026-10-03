package bio.cosy.feddb.core.api.pipeline;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import jakarta.ws.rs.NotFoundException;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Date;
import java.util.List;

@Setter
@Getter
@MappedSuperclass
public abstract class BasePipelineEntity<S extends BasePipelineStepEntity<?>> extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "pipeline_status")
    private PipelineStatus pipelineStatus;

    private String secret;

    @Column(length = 64, name = "docker_tag")
    private String dockerTag;

    @Column(name = "container_id")
    private String containerId;

    @Column(name = "publish_info", columnDefinition = "json")
    @JdbcTypeCode(SqlTypes.JSON)
    private AppPublishInfoDTO publishInfo;

    public abstract List<S> getSteps();

    public void apply(PipelineStatusUpdateDTO dto) {
        getSteps().stream().filter(step -> step.getName() == dto.getStepName()).findFirst()
                .orElseThrow(() -> new NotFoundException("Step not found: " + dto.getStepName()))
                .apply(dto);
        boolean failed = getSteps().stream().anyMatch(step -> step.getStepStatus() == PipelineStatus.FAILED);
        boolean succeeded = getSteps().stream().filter(step -> step.getName() != StepName.PREFLIGHT)
                .allMatch(step -> step.getStepStatus() == PipelineStatus.SUCCESS || step.getStepStatus() == PipelineStatus.WARNING);
        pipelineStatus = failed ? PipelineStatus.FAILED : succeeded ? PipelineStatus.SUCCESS : PipelineStatus.RUNNING;
        setUpdatedAt(new Date());
    }
}

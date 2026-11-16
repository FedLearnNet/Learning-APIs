package bio.cosy.feddb.core.execution.experiment;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.config.ExecutionDiagramConfigDTO;
import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * What a developer or researcher sets up to learn something: one or more runs of the execution with charts
 * over their trials, per tool of the workflow. Local and Global bind it to what owns their experiments.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class BaseExecutionExperimentEntity extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diagram_configs", columnDefinition = "jsonb")
    private List<ExecutionDiagramConfigDTO> diagramConfigs = new ArrayList<>();
}

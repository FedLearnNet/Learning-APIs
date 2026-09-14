package bio.cosy.feddb.core.api.workflow.base.experiment;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.util.Date;
import java.util.Set;

@Setter
@Getter
@MappedSuperclass
public class BaseWorkflowExperimentEntity<T extends BaseWorkflowStepEntity<?, ?>> extends BaseEntity {

    @Column(name = "finished_at")
    private Date finishedAt;

    @Column(name = "started_at")
    private Date startedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "experiment_status")
    private ProjectStatus experimentStatus;

    @ManyToOne(cascade = {CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "current_step_id", nullable = true)
    private T currentWorkflowNode;

    @OneToMany(mappedBy = "experiment",
            orphanRemoval = true,
            cascade = {CascadeType.ALL})
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Set<T> steps;
}

package de.unihamburg.daibetes.api.runs.experiment.message;

import bio.cosy.feddb.core.api.run.message.RunMessageTypes;
import de.unihamburg.daibetes.api.runs.experiment.run.ExperimentRunEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Entity
@Table(name = "experiments_run_messages")
public class ExperimentRunMessageEntity extends BaseEntity {

    private String process;

    @Enumerated(EnumType.STRING)
    private RunMessageTypes type;

    @Column(columnDefinition="TEXT")
    private String message;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "experiment_run_id", nullable = false)
    private ExperimentRunEntity run;

}

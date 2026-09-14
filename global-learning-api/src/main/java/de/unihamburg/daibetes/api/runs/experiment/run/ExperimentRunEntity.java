package de.unihamburg.daibetes.api.runs.experiment.run;

import bio.cosy.feddb.core.api.run.RunMetaDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.api.runs.experiment.ExperimentEntity;
import de.unihamburg.daibetes.api.runs.experiment.message.ExperimentRunMessageEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "experiments_runs")
public class ExperimentRunEntity extends BaseEntity {

    private String name;
    private String color;

    @Enumerated(EnumType.STRING)
    private RunStatusTypes status;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(columnDefinition = "TEXT")
    private String hyperParams;

    @Column(columnDefinition = "TEXT")
    private String output;

    // Run metadata (timings + reserved fields) as JSON metadata, nullable for historical runs.
    // Extensible without a schema change. RUNTIME always stored; OVERHEAD_* only when
    // posymed.runtime.overhead.enabled is true.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", columnDefinition = "jsonb")
    private RunMetaDTO meta;

    @ManyToOne(cascade = {CascadeType.MERGE, CascadeType.REFRESH})
    @JoinColumn(name = "experiment_id", nullable = false)
    private ExperimentEntity experiment;

    @OneToMany(mappedBy = "run", cascade = {CascadeType.MERGE, CascadeType.REFRESH})
    private Set<ExperimentRunMessageEntity> messages;

    @OneToOne(mappedBy = "experimentRun", cascade = {CascadeType.MERGE, CascadeType.REFRESH})
    private ModelSubEntity modelSub;
}

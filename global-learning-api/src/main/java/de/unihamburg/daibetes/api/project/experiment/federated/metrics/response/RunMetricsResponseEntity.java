package de.unihamburg.daibetes.api.project.experiment.federated.metrics.response;

import bio.cosy.feddb.core.api.socket.RunMetricDTO;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.metrics.RunMetricsRequestEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(name = "run_metrics_response")
@Getter
@Setter
public class RunMetricsResponseEntity extends BaseEntity {

    @Column(name = "random_clinic_id", nullable = false)
    private String randomClinicId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics", columnDefinition = "jsonb")
    private List<RunMetricDTO> metrics;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "metrics_request_id", nullable = false)
    private RunMetricsRequestEntity request;
}

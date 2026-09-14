package de.unihamburg.daibetes.api.query.statistics;

import bio.cosy.feddb.core.api.socket.DataStatisticsDTO;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.query.QueryEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "data_statistics_result")
@Setter
@Getter
public class DataStatisticsResponseEntity extends BaseEntity {

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "statistics", columnDefinition = "jsonb")
    private DataStatisticsDTO statistics;

    @Column(name = "random_clinic_id")
    private String randomClinicId;

    @Column(name = "request_id")
    private UUID requestId;

    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "query_id", nullable = false)
    private QueryEntity query;
}

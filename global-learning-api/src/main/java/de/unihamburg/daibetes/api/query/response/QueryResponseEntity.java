package de.unihamburg.daibetes.api.query.response;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.services.orch.dto.SystemComputingInfoReducedDTO;
import de.unihamburg.daibetes.api.query.QueryEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

import java.util.Set;

@Entity(name = "QueryResponseEntity")
@Table(name = "query_result")
@Setter
@Getter
public class QueryResponseEntity extends BaseEntity {


    @ManyToOne(cascade = CascadeType.REFRESH)
    @JoinColumn(name = "query_id", nullable = false)
    private QueryEntity query;
    private Integer count;
    private String error;

    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private SystemComputingInfoReducedDTO system;

    @Column(name = "supported_modes")
    @JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private Set<ExecutionMode> supportedModes;

    //TODO might be a problem, but cant come up with a better solution
    @Column(name = "connection_id")
    private String connectionId;

    // Keycloak name of the client, only when it shares it for TRE
    @Column(name = "client_name")
    private String clientName;
}

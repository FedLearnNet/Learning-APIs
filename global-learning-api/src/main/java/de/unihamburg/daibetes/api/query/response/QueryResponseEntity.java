package de.unihamburg.daibetes.api.query.response;

import de.unihamburg.daibetes.api.query.QueryEntity;
import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

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
}

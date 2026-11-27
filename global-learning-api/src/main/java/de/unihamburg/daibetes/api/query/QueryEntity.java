package de.unihamburg.daibetes.api.query;

import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.base.BaseAuthEntity;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Date;
import java.util.Set;
import java.util.UUID;

@Entity(name = "QueryEntity")
@Table(name = "queries")
@Setter
@Getter
public class QueryEntity extends BaseAuthEntity {

    @Column(name = "group_id", nullable = false)
    private String groupId;

    @Column(name = "global_unique_id", unique = true, nullable = false)
    private String globalUniqueId;

    private String name;
    private String description;

    @Column(name = "latest_data_statistics_request")
    private UUID latestDataStatisticsRequest;

    @Column(name = "latest_data_statistics_request_timestamp")
    private Date latestDataStatisticsRequestTimestamp;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "query_string", columnDefinition = "jsonb")
    private QueryItemDTO query;

    private Integer result;

    @Column(name = "has_result", columnDefinition = "boolean default false")
    private boolean hasResult;

    @Column(name = "has_fired", columnDefinition = "boolean default false")
    private boolean hasFired;

    // clients connected at fire time; the result shows once all answered, at the latest at the deadline
    @Column(name = "expected_answers")
    private Integer expectedAnswers;

    @OneToMany(mappedBy = "query", cascade = {
            CascadeType.PERSIST,
            CascadeType.REMOVE,
            CascadeType.REFRESH,
            CascadeType.DETACH
    })
    private Set<ProjectEntity> projects;

}

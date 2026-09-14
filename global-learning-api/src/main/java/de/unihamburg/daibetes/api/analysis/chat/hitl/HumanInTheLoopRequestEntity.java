package de.unihamburg.daibetes.api.analysis.chat.hitl;

import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.agent.anlysis.PlanState;
import de.unihamburg.daibetes.api.analysis.chat.DataAnalysisChatMessageEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Setter
@Getter
@Entity
@Table(name = "data_analysis_chat_human_in_the_loop_request")
public class HumanInTheLoopRequestEntity extends BaseEntity {

    @Column(columnDefinition = "TEXT")
    private String request;

    @Column(columnDefinition = "TEXT")
    private String answer;

    @Column(name = "is_answered", nullable = false, columnDefinition = "boolean default false")
    private Boolean isAnswered;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tools", columnDefinition = "jsonb")
    private PlanState state;

    @ManyToOne
    @JoinColumn(name = "message_id", nullable = false)
    private DataAnalysisChatMessageEntity message;
}

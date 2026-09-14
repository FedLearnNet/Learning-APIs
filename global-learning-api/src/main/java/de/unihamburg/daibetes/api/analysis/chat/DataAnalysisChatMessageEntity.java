package de.unihamburg.daibetes.api.analysis.chat;

import bio.cosy.feddb.core.api.model.chat.ToolDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.HumanInTheLoopDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.ReasoningDTO;
import bio.cosy.feddb.core.api.model.workflow.chat.UiActionDTO;
import bio.cosy.feddb.core.base.BaseEntity;
import de.unihamburg.daibetes.api.analysis.DataAnalysisEntity;
import de.unihamburg.daibetes.api.analysis.chat.hitl.HumanInTheLoopRequestEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Set;

@Setter
@Getter
@Entity
@Table(name = "data_analysis_chat_message")
public class DataAnalysisChatMessageEntity extends BaseEntity {

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_request")
    private boolean isRequest;

    @Column(name = "is_done")
    private boolean isDone;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "status_message", columnDefinition = "TEXT")
    private String statusMessage;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "action", columnDefinition = "jsonb")
    private UiActionDTO action;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tools", columnDefinition = "jsonb")
    private List<ToolDTO> tools;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reasonings", columnDefinition = "jsonb")
    private List<ReasoningDTO> reasonings;

    @ManyToOne
    @JoinColumn(name = "workflow_id", nullable = true)
    private DataAnalysisEntity dataAnalysis;

    @OneToMany(mappedBy = "message")
    private Set<HumanInTheLoopRequestEntity> humanInTheLoopRequests;
}

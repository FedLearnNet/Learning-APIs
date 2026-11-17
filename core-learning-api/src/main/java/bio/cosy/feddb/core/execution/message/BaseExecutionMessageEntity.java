package bio.cosy.feddb.core.execution.message;

import bio.cosy.feddb.core.base.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Setter
@Getter
@MappedSuperclass
public class BaseExecutionMessageEntity extends BaseEntity {

    private String process;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, updatable = false)
    private RunMessageTypes type;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message", columnDefinition = "jsonb",
            nullable = false, updatable = false)
    private ExecutionMessageDTO payload;
}

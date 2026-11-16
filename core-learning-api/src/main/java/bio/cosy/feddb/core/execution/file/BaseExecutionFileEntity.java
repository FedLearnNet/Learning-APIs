package bio.cosy.feddb.core.execution.file;

import bio.cosy.feddb.core.base.BaseEntity;
import bio.cosy.feddb.core.base.BaseFileEntity;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Getter
@Setter
@MappedSuperclass
public abstract class BaseExecutionFileEntity<
        Attempt extends BaseExecutionAttemptEntity<?, ?>,
        File extends BaseFileEntity> extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Attempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private File file;

    // Input or output name the app knows the file by.
    @Column(nullable = false, updatable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private ExecutionFileDirection direction;
}

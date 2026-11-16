package bio.cosy.feddb.local.execution.file;

import bio.cosy.feddb.core.execution.file.BaseExecutionFileEntity;
import bio.cosy.feddb.local.api.file.FileEntity;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "execution_file")
public class ExecutionFileEntity extends BaseExecutionFileEntity<ExecutionAttemptEntity, FileEntity> {
}

package de.unihamburg.daibetes.execution.file;

import bio.cosy.feddb.core.execution.file.BaseExecutionFileEntity;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "execution_file")
public class ExecutionFileEntity extends BaseExecutionFileEntity<ExecutionAttemptEntity, FileEntity> {
}

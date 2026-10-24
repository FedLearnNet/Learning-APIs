package de.unihamburg.daibetes.execution.file;

import bio.cosy.feddb.core.execution.file.BaseExecutionFileBO;
import de.unihamburg.daibetes.api.file.FileBO;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExecutionFileBO extends BaseExecutionFileBO<ExecutionFileDTO, ExecutionAttemptEntity, FileEntity, FileBO,
        ExecutionFileEntity, ExecutionFileAO, ExecutionFileMapper> {

    public ExecutionFileBO() {
        super(ExecutionFileEntity::new);
    }

    @Override
    protected String ownerOf(ExecutionAttemptEntity attempt) {
        return attempt.getStep().getRun().getKeycloakId();
    }
}

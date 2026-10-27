package bio.cosy.feddb.local.execution.file;

import bio.cosy.feddb.core.execution.file.BaseExecutionFileBO;
import bio.cosy.feddb.local.api.file.FileBO;
import bio.cosy.feddb.local.api.file.FileEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class ExecutionFileBO extends BaseExecutionFileBO<ExecutionFileDTO, ExecutionAttemptEntity, FileEntity, FileBO,
        ExecutionFileEntity, ExecutionFileAO, ExecutionFileMapper> {

    @Inject
    FLNetClientConfig config;

    public ExecutionFileBO() {
        super(ExecutionFileEntity::new);
    }

    @Override
    protected String ownerOf(ExecutionAttemptEntity attempt) {
        return config.user().systemUserName();
    }
}

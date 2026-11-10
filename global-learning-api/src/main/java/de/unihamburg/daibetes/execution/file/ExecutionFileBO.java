package de.unihamburg.daibetes.execution.file;

import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.file.BaseExecutionFileBO;
import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import de.unihamburg.daibetes.api.file.FileBO;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunDetailMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ExecutionFileBO extends BaseExecutionFileBO<ExecutionFileDTO, ExecutionAttemptEntity, FileEntity, FileBO,
        ExecutionFileEntity, ExecutionFileAO, ExecutionFileMapper> {

    @Inject
    ExecutionEventPublisher events;

    @Inject
    ExecutionRunDetailMapper resultMapper;

    @Override
    @Transactional
    public void keepOutputs(ExecutionAttemptEntity attempt, AppRunUploadData upload) {
        super.keepOutputs(attempt, upload);
        var fields = resultMapper.mapJsonToFields(attempt.getResult());
        fields.putAll(upload.getFieldsNullsafe());
        attempt.setResult(resultMapper.mapFieldsToJson(fields));
        events.attemptUpdated(attempt.getId());
    }

    public ExecutionFileBO() {
        super(ExecutionFileEntity::new);
    }

    @Override
    protected String ownerOf(ExecutionAttemptEntity attempt) {
        return attempt.getStep().getRun().getKeycloakId();
    }
}

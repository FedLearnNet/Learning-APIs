package bio.cosy.feddb.local.execution.file;

import bio.cosy.feddb.core.execution.file.BaseExecutionFileBO;
import bio.cosy.feddb.core.execution.file.ExecutionFileDirection;
import bio.cosy.feddb.local.api.file.FileBO;
import bio.cosy.feddb.local.api.file.FileEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.execution.attempt.ExecutionAttemptEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class ExecutionFileBO extends BaseExecutionFileBO<ExecutionFileDTO, ExecutionAttemptEntity, FileEntity, FileBO,
        ExecutionFileEntity, ExecutionFileAO, ExecutionFileMapper> {

    @Inject
    FLNetClientConfig config;

    @Inject
    FileBO files;

    public ExecutionFileBO() {
        super(ExecutionFileEntity::new);
    }

    @Override
    protected String ownerOf(ExecutionAttemptEntity attempt) {
        return config.user().systemUserName();
    }

    public List<ExecutionFileDTO> storeAndLinkFiles(ExecutionAttemptEntity attempt, List<File> written, ExecutionFileDirection direction) {
        Map<String, FileEntity> stored = new LinkedHashMap<>();
        written.forEach(file -> stored.putIfAbsent(FilenameUtils.getBaseName(file.getName()), files.createEntityForSystem(file)));
        return linkFiles(attempt, stored, direction);
    }
}

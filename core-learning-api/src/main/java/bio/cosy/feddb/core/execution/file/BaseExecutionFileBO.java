package bio.cosy.feddb.core.execution.file;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.BaseFileBO;
import bio.cosy.feddb.core.base.BaseFileEntity;
import bio.cosy.feddb.core.execution.attempt.BaseExecutionAttemptEntity;
import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public abstract class BaseExecutionFileBO<
        DTO extends BaseExecutionFileDTO,
        Attempt extends BaseExecutionAttemptEntity<?, ?>,
        F extends BaseFileEntity,
        FileBO extends BaseFileBO<FileDTO, F, ?, ?>,
        Entity extends BaseExecutionFileEntity<Attempt, F>,
        AO extends BaseExecutionFileAO<Entity>,
        Mapper extends BaseExecutionFileMapper<DTO, Entity>>
        extends BaseBo<DTO, Entity, AO, Mapper> {

    @Inject
    FileBO fileBO;

    private final Supplier<Entity> newEntity;

    protected BaseExecutionFileBO(Supplier<Entity> newEntity) {
        this.newEntity = newEntity;
    }

    protected abstract String ownerOf(Attempt attempt);

    public F storeFile(Attempt attempt, FileUpload upload) {
        return storeFile(upload, ownerOf(attempt));
    }

    public F storeFile(FileUpload upload, String keycloakId) {
        return fileBO.createEntity(new FileDTO(), keycloakId, upload);
    }

    public File loadFile(F file) {
        return fileBO.loadFile(file);
    }

    public List<DTO> linkFiles(Attempt attempt, Map<String, F> files, ExecutionFileDirection direction) {
        List<DTO> inputs = new ArrayList<>();
        files.forEach((name, file) -> {
            DTO input = create(attempt, file, name, direction);
            input.setContent(loadFile(file));
            inputs.add(input);
        });
        return inputs;
    }

    public List<DTO> linkInput(Attempt attempt, Map<String, F> files) {
        return linkFiles(attempt, files, ExecutionFileDirection.INPUT);
    }

    public List<DTO> linkOutput(Attempt attempt, Map<String, F> files) {
        return linkFiles(attempt, files, ExecutionFileDirection.OUTPUT);
    }

    public LinkedHashMap<String, String> inputFilePathsOf(Long attemptId) {
        LinkedHashMap<String, String> paths = new LinkedHashMap<>();
        listForAttempt(attemptId, ExecutionFileDirection.INPUT)
                .forEach(input -> paths.put(input.getName(), input.getFileNameWithExtension()));
        return paths;
    }

    public List<DTO> addOutputs(Attempt attempt, List<FileUpload> uploads) {
        List<DTO> outputs = new ArrayList<>();
        for (FileUpload upload : uploads) {
            String name = AppRunUploadData.keyOf(upload);
            try {
                F storedFile = storeFile(attempt, upload);
                DTO created = create(attempt, storedFile, name, ExecutionFileDirection.OUTPUT);
                outputs.add(created);
            } catch (RuntimeException e) {
                Log.errorf(e, "Could not store output %s of attempt %d", name, attempt.getId());
            }
        }
        return outputs;
    }

    public List<DTO> listForAttempt(Long attemptId, ExecutionFileDirection direction) {
        return mapper.entitiesToDtos(ao.listForAttempt(attemptId, direction));
    }

    public List<DTO> listForRun(Long runId, ExecutionFileDirection direction) {
        return mapper.entitiesToDtos(ao.listForRun(runId, direction));
    }

    private DTO create(Attempt attempt, F file, String name, ExecutionFileDirection direction) {
        Entity entity = newEntity.get();
        entity.setAttempt(attempt);
        entity.setFile(file);
        entity.setName(name);
        entity.setDirection(direction);
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }
}

package de.unihamburg.daibetes.api.analysis.file;

import bio.cosy.feddb.core.api.file.FileContentDTO;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.FileRenameDTO;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import bio.cosy.feddb.core.execution.run.dto.AppRunUploadData;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.BaseFileEntity;
import bio.cosy.feddb.core.helper.FileHelper;
import de.unihamburg.daibetes.api.analysis.DataAnalysisBO;
import de.unihamburg.daibetes.api.analysis.DataAnalysisEntity;
import de.unihamburg.daibetes.api.file.FileBO;
import de.unihamburg.daibetes.api.file.FileEntity;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;


@ApplicationScoped
public class DataAnalysisFileBO extends BaseBo<DataAnalysisFileDTO, DataAnalysisFileEntity, DataAnalysisFileAO, DataAnalysisFileMapper> {

    @Inject
    DataAnalysisBO dataAnalysisBO;

    @Inject
    FileBO fileBo;

    public DataAnalysisFileDTO findById(Long id, String keycloakId) {
        return ao.getById(id, keycloakId)
                .map(f -> mapper.entityToDto(f))
                .orElseThrow(() -> new NotFoundException("Workflow file not found"));
    }

    public DataAnalysisFileDTO findById(Long id, Long workflowId, String keycloakId) {
        return ao.getById(id, workflowId, keycloakId)
                .map(f -> mapper.entityToDto(f))
                .orElseThrow(() -> new NotFoundException("Workflow file not found"));
    }

    public List<DataAnalysisFileDTO> listAll(Long workflowId, String keycloakId, boolean includeInputFiles) {
        return ao.getAll(workflowId, keycloakId)
                .stream()
                .filter(f -> includeInputFiles || f.getInputName() == null)
                .map(f -> mapper.entityToDto(f))
                .toList();
    }

    public List<DataAnalysisFileDTO> listAll(String keycloakId, boolean includeInputFiles) {
        return ao.getAll(keycloakId)
                .stream()
                .filter(f -> includeInputFiles || f.getInputName() == null)
                .map(f -> mapper.entityToDto(f))
                .toList();
    }


    public FileContentDTO getFileContent(Long id, Long fileId, String keycloakId) {
        FileEntity file = ao.getById(fileId, id, keycloakId)
                .map(DataAnalysisFileEntity::getFile)
                .orElseThrow(() -> new NotFoundException("File not found"));
        return fileBo.getFileContent(file);
    }

    public String getFileContentForAnalyzing(Long id, Long fileId, String keycloakId) {
        FileEntity file = ao.getByFileId(fileId, id, keycloakId)
                .map(DataAnalysisFileEntity::getFile)
                .orElseThrow(() -> new NotFoundException("File not found"));
        return fileBo.getFileContentForAnalyzing(file);
    }

    public FileContentDTO getFileContent(DataAnalysisFileEntity dataAnalysisFileEntity) {
        FileEntity file = dataAnalysisFileEntity.getFile();
        return fileBo.getFileContent(file);
    }

    public DataAnalysisFileEntity getFileEntity(Long id, Long fileId, String keycloakId) {
        return ao.getById(fileId, id, keycloakId)
                .orElseThrow(() -> new NotFoundException("File not found"));
    }

    public FileProfile getFileStatistics(Long id, Long fileId, String keycloakId) {
        FileEntity file = ao.getById(fileId, id, keycloakId)
                .map(DataAnalysisFileEntity::getFile)
                .orElseThrow(() -> new NotFoundException("File not found"));
        return fileBo.getFileStatistics(file);
    }

    public FileProfile getFileStatisticsByName(String fileName) {
        FileEntity file = ao.getByFileName(fileName)
                .map(DataAnalysisFileEntity::getFile)
                .orElseThrow(() -> new NotFoundException("File not found"));

        return fileBo.getFileStatistics(file);
    }

    public FileContentDTO getFileContentByName(String fileName) {
        FileEntity file = ao.getByFileName(fileName)
                .map(DataAnalysisFileEntity::getFile)
                .orElseThrow(() -> new NotFoundException("File not found"));
        return fileBo.getFileContent(file);
    }


    public DataAnalysisFileDTO renameFile(Long id, Long fileId, FileRenameDTO fileRenameDTO, String keycloakId) {
        FileEntity file = ao.getById(fileId, id, keycloakId)
                .map(DataAnalysisFileEntity::getFile)
                .orElseThrow(() -> new NotFoundException("File not found"));
        fileBo.renameFile(file, fileRenameDTO);
        return findById(fileId, id, keycloakId);
    }

    public void storeAttemptOutputs(ExecutionAttemptEntity attempt, DataAnalysisEntity dataAnalysis, String keycloakId,
                                    List<FileUpload> files) {
        for (FileUpload file : files) {
            String key = AppRunUploadData.getKey(file);
            try {
                DataAnalysisFileEntity datafileEntity = new DataAnalysisFileEntity();
                FileEntity createdFile = fileBo.createEntity(new FileDTO(), keycloakId, file);
                datafileEntity.setOutputName(key);
                datafileEntity.setAttempt(attempt);
                create(dataAnalysis, createdFile, keycloakId, datafileEntity);
            } catch (Exception e) {
                Log.error("Failed to store file for key, use dn store as backup: " + key, e);
            }
        }
    }

    public DataAnalysisFileDTO storeFile(Long dataAnalysisId, FileUpload file, String keycloakId) {
        FileEntity createdFile = fileBo.createEntity(new FileDTO(), keycloakId, file);
        DataAnalysisFileEntity fileEntity = new DataAnalysisFileEntity();
        return create(dataAnalysisId, createdFile, keycloakId, fileEntity);
    }

    public DataAnalysisFileDTO linkFile(Long dataAnalysisId, Long fileId, String keycloakId) {
        FileEntity createdFile = fileBo.getEntityById(fileId, keycloakId);
        DataAnalysisFileEntity fileEntity = new DataAnalysisFileEntity();
        return create(dataAnalysisId, createdFile, keycloakId, fileEntity);
    }

    public DataAnalysisFileDTO create(DataAnalysisEntity workflow, FileEntity file, String keycloakId, DataAnalysisFileEntity fileEntity) {
        fileEntity.setKeycloakId(keycloakId);
        fileEntity.setDataAnalysis(workflow);
        fileEntity.setFile(file);
        ao.persist(fileEntity);
        return mapper.entityToDto(fileEntity);
    }

    public DataAnalysisFileDTO create(Long dataAnalysisId, FileEntity file, String keycloakId, DataAnalysisFileEntity fileEntity) {
        DataAnalysisEntity workflow = dataAnalysisBO.findEntityById(dataAnalysisId, keycloakId);
        return create(workflow, file, keycloakId, fileEntity);
    }

    public void delete(Long id, Long workflowId, String keycloakId) {
        DataAnalysisFileEntity file = ao.getById(id, workflowId, keycloakId)
                .orElseThrow(() -> new NotFoundException("Workflow file not found"));
        ao.delete(file);
    }

    public byte[] downloadOutput(Set<DataAnalysisFileEntity> files) {
        Map<BaseFileEntity, File> fileMap = files.stream()
                .map(DataAnalysisFileEntity::getFile)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toMap(
                        Function.identity(),
                        fileBo::loadFile
                ));
        return FileHelper.filesToZip(fileMap);
    }

    public byte[] downloadOutputForRun(Long runId, String keycloakId) {
        Map<String, File> zipEntries = new LinkedHashMap<>();
        for (DataAnalysisFileEntity dataAnalysisFile : ao.getAllForRun(runId, keycloakId)) {
            FileEntity fileEntity = dataAnalysisFile.getFile();
            File file = fileEntity == null ? null : fileBo.loadFile(fileEntity);
            if (file == null || !file.exists() || !file.isFile()) {
                continue;
            }
            // A single app or model has no node and is the run's only step.
            WorkflowNodeEntity node = dataAnalysisFile.getAttempt().getStep().getWorkflowNode();
            String stepFolder = "step-" + (node != null && node.getExecutionOrder() != null ? node.getExecutionOrder() + 1 : 1);
            String subFolder = dataAnalysisFile.getInputName() != null ? "input"
                    : dataAnalysisFile.getOutputName() != null ? "output" : "other";
            String zipPath = makeUnique(zipEntries, stepFolder + "/" + subFolder + "/" + fileEntity.getFileName());
            zipEntries.put(zipPath, file);
        }
        return FileHelper.filesToZipByPath(zipEntries);
    }

    private String makeUnique(Map<String, File> existing, String path) {
        if (!existing.containsKey(path)) {
            return path;
        }

        int dotIndex = path.lastIndexOf('.');
        String base = dotIndex >= 0 ? path.substring(0, dotIndex) : path;
        String ext = dotIndex >= 0 ? path.substring(dotIndex) : "";

        int counter = 1;
        String candidate;
        do {
            candidate = base + "_" + counter + ext;
            counter++;
        } while (existing.containsKey(candidate));

        return candidate;
    }

}

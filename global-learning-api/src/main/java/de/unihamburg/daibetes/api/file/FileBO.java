package de.unihamburg.daibetes.api.file;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.FileRenameDTO;
import bio.cosy.feddb.core.api.file.analytics.FileTypeAnalyzer;
import bio.cosy.feddb.core.base.BaseFileBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

@ApplicationScoped
public class FileBO extends BaseFileBO<FileDTO, FileEntity, FileAO, FileMapper> {

    public List<FileDTO> list(String keycloakId) {
        return mapper.entitiesToDtos(
                ao.getAll(keycloakId)
                        .stream()
                        .peek(this::renewSecret)
                        .toList()
        );
    }

    public FileDTO renameFileBySecret(Long id, FileRenameDTO fileRenameDTO, String keycloakId) {
        FileEntity file = getEntityByIdAndSecret(id, keycloakId);
        file.setFileName(fileRenameDTO.getName());
        ao.persist(file);
        return mapper.entityToDto(file);
    }

    public FileDTO renameFile(Long id, FileRenameDTO fileRenameDTO, String keycloakId) {
        FileEntity file = getEntityById(id, keycloakId);
        file.setFileName(fileRenameDTO.getName());
        ao.persist(file);
        return mapper.entityToDto(file);
    }

    public FileDTO renameFile(FileEntity file, FileRenameDTO fileRenameDTO) {
        file.setFileName(fileRenameDTO.getName());
        ao.persist(file);
        return mapper.entityToDto(file);
    }

    public List<FileDTO> create(List<File> files) {
        String keycloakId = "";
        return files.stream()
                .map(file -> create(file, keycloakId))
                .toList();
    }

    public List<FileDTO> create(List<File> files, String keycloakId) {
        return files.stream()
                .map(file -> create(file, keycloakId))
                .toList();
    }

    public FileEntity createEntity(File file, String originalName, String keycloakId) {
        FileEntity entity = new FileEntity();
        entity.setKeycloakId(keycloakId);
        long size = FileUtils.sizeOf(file);
        String contentType = "application/octet-stream";
        try {
            contentType = FileTypeAnalyzer.analyseContentType(file, originalName);
        } catch (Exception e) {
            Log.errorf("Failed to FileTypeAnalyzer.analyseContentType: %s", e.getMessage(), e);
        }

        try (InputStream in = new FileInputStream(file)) {
            this.saveFile(entity, in, originalName, size, contentType);
        } catch (Exception e) {
            Log.errorf("Failed to process uploaded file: %s", e.getMessage(), e);
            throw new IllegalStateException("Failed to process uploaded file", e);
        }
        entity.setSecret(getSecret());
        ao.persist(entity);
        return entity;
    }

    public FileDTO create(File file, String originalName, String keycloakId) {
        FileEntity entity = createEntity(file, originalName, keycloakId);
        return mapper.entityToDto(entity);
    }

    public FileDTO create(File file, String keycloakId) {
        return create(file, file.getName(), keycloakId);
    }

}

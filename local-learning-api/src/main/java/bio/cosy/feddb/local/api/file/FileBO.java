package bio.cosy.feddb.local.api.file;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.FileRenameDTO;
import bio.cosy.feddb.core.api.file.analytics.FileTypeAnalyzer;
import bio.cosy.feddb.core.base.BaseFileBO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

@ApplicationScoped
public class FileBO extends BaseFileBO<FileDTO, FileEntity, FileAO, FileMapper> {

    @Inject
    FLNetClientConfig config;

    public List<FileDTO> list(String keycloakId) {
        return mapper.entitiesToDtos(ao.getAll(keycloakId));
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

    public FileDTO create(File file, String keycloakId) {
        FileEntity entity = new FileEntity();
        entity.setKeycloakId(keycloakId);
        String originalName = file.getName();
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
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }

    public FileEntity createEntityForSystem(File file) {
        FileEntity entity = new FileEntity();
        entity.setKeycloakId(config.user().systemUserName());
        String originalName = file.getName();
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
        ao.persist(entity);
        return entity;
    }


}

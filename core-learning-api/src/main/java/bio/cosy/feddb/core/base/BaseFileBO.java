package bio.cosy.feddb.core.base;

import bio.cosy.feddb.core.api.app.config.ToolConfigDataType;
import bio.cosy.feddb.core.api.file.FileContentDTO;
import bio.cosy.feddb.core.api.file.FileResult;
import bio.cosy.feddb.core.api.file.analytics.FileAnalytics;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.core.api.file.analytics.FileTypeAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.RandomStringUtils;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.server.multipart.FormValue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Optional;

public abstract class BaseFileBO<Dto extends BaseDTO, Entity extends BaseFileEntity, Ao extends PanacheRepository<Entity>,
        Mapper extends BaseMapper<Dto, Entity>> extends BaseBo<Dto, Entity, Ao, Mapper> {

    @Inject
    BaseFileStoreAO fileStoreAO;

    @Inject
    FileAnalytics fileAnalytics;


    public Dto create(Dto request, String keycloakId, FormValue file) throws IOException {
        Entity entity = createEntity(request, keycloakId, file);
        Dto dto = mapper.entityToDto(entity);
        return dto;
    }

    public Entity createEntity(Dto request, String keycloakId, FormValue file) throws IOException {
        Entity entity = mapper.dtoToEntity(request);
        entity.setKeycloakId(keycloakId);
        String originalName = file.getFileName();
        long size = file.getFileItem().getFileSize();
        String contentType = "application/octet-stream";
        try {
            contentType = FileTypeAnalyzer.analyseContentType(file.getFileItem().getFile().toFile(), originalName);
        } catch (Exception e) {
            Log.errorf("Failed to FileTypeAnalyzer.analyseContentType: %s", e.getMessage(), e);
        }

        try (InputStream in = new FileInputStream(file.getFileItem().getFile().toFile())) {
            this.saveFile(entity, in, originalName, size, contentType);
        } catch (Exception e) {
            Log.errorf("Failed to process uploaded file: %s", e.getMessage(), e);
            throw new IllegalStateException("Failed to process uploaded file", e);
        }
        ao.persist(entity);
        return entity;
    }


    public Dto create(Dto request, String keycloakId, FileUpload file) {
        Entity entity = createEntity(request, keycloakId, file);
        Dto dto = mapper.entityToDto(entity);
        return dto;
    }

    public Dto create(Dto request, String keycloakId, File file, String filename) {
        Entity entity = createEntity(request, keycloakId, file, filename);
        Dto dto = mapper.entityToDto(entity);
        return dto;
    }

    public Entity createEntity(Dto request, String keycloakId, FileUpload file) {
        Entity entity = mapper.dtoToEntity(request);
        entity.setKeycloakId(keycloakId);
        String originalName = file.fileName();
        long size = file.size();
        String contentType = "application/octet-stream";
        try {
            contentType = FileTypeAnalyzer.analyseContentType(file.uploadedFile().toFile(), originalName);
        } catch (Exception e) {
            Log.errorf("Failed to FileTypeAnalyzer.analyseContentType: %s", e.getMessage(), e);
        }

        try (InputStream in = new FileInputStream(file.uploadedFile().toFile())) {
            this.saveFile(entity, in, originalName, size, contentType);
        } catch (Exception e) {
            Log.errorf("Failed to process uploaded file: %s", e.getMessage(), e);
            throw new IllegalStateException("Failed to process uploaded file", e);
        }
        ao.persist(entity);
        return entity;
    }

    public Entity createEntity(Dto request, String keycloakId, File file, String filename) {
        Entity entity = mapper.dtoToEntity(request);
        entity.setKeycloakId(keycloakId);

        String originalName = filename != null && !filename.isBlank() ? filename : file.getName();
        long size = file.length();
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


    public Entity getEntityById(Long id, String keycloakId) {
        Optional<Entity> entity = ao.findByIdOptional(id);
        if (entity.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        if (!entity.get().getKeycloakId().equals(keycloakId)) {
            Log.warnf("Property with the ID %s not found for user %s", id, keycloakId);
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        return entity.get();
    }

    public Entity getEntityByIdAndSecret(Long id, String secret) {
        Optional<Entity> entity = ao.findByIdOptional(id);
        if (entity.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        if (!entity.get().getSecret().equals(secret)) {
            Log.warnf("Property with the ID %s not found for secret %s", id, secret);
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        return entity.get();
    }

    public Dto getById(Long id, String keycloakId) {
        Optional<Entity> entity = ao.findByIdOptional(id);
        if (entity.isEmpty()) {
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        if (!entity.get().getKeycloakId().equals(keycloakId)) {
            Log.warnf("Property with the ID %s not found for user %s", id, keycloakId);
            throw new NotFoundException(String.format("Property with the ID %s not found", id));
        }
        Entity found = entity.get();
        renewSecret(found);
        Dto dto = mapper.entityToDto(found);
        return dto;
    }

    public Dto getByIdAndSecret(Long id, String keycloakId) {
        Entity entity = getEntityByIdAndSecret(id, keycloakId);
        renewSecret(entity);
        Dto dto = mapper.entityToDto(entity);
        return dto;
    }

    public void deleteById(Long id, String keycloakId) {
        Entity entity = getEntityById(id, keycloakId);
        ao.delete(entity);
    }

    public void deleteByIdAndSecret(Long id, String secret) {
        Entity entity = getEntityByIdAndSecret(id, secret);
        ao.delete(entity);
    }

    public void deleteStoredFile(Long objectId) {
        fileStoreAO.deleteStoredFile(objectId);
    }

    public void saveFile(Entity entity, InputStream file, String name, Long size, String contentType) {

        entity.setContentType(contentType);
        entity.setFileName(name);
        entity.setSize(size);
        entity.setSecret(getSecret());
        Long oId = fileStoreAO.storeFile(file);
        if (oId == null) {
            throw new IllegalStateException("Failed to store file");
        }
        entity.setLargeObjectId(oId);
    }

    public FileResult loadFile(Long id, String keycloakId) {
        Entity entity = getEntityById(id, keycloakId);
        File f = fileStoreAO.loadFile(entity.getLargeObjectId());
        String name = entity.getFileName();
        return new FileResult(f, name);
    }

    public FileResult loadFileSecret(Long id, String secret) {
        Entity entity = getEntityByIdAndSecret(id, secret);
        File f = fileStoreAO.loadFile(entity.getLargeObjectId());
        String name = entity.getFileName();
        return new FileResult(f, name);
    }

    public FileResult loadFileResult(Entity entity) {
        File f = fileStoreAO.loadFile(entity.getLargeObjectId());
        String name = entity.getFileName();
        return new FileResult(f, name);
    }

    public File loadFile(Long id) {
        Entity entity = ao.findById(id);
        if (entity == null) {
            throw new IllegalStateException("Entity not found");
        }
        return fileStoreAO.loadFile(entity.getLargeObjectId());
    }

    public File loadFile(Entity entity) {
        if (entity.getLargeObjectId() == null) {
            throw new IllegalStateException("No file associated with this entity");
        }
        return fileStoreAO.loadFile(entity.getLargeObjectId());
    }

    public String loadFileAsBase64(Long id) {
        Entity entity = ao.findById(id);
        if (entity == null) {
            throw new IllegalStateException("Entity not found");
        }

        if (entity.getLargeObjectId() == null) {
            throw new IllegalStateException("No file associated with this entity");
        }
        return fileStoreAO.loadFileAsBase64(entity.getLargeObjectId());
    }

    public File loadFile(Dto dto) {
        if (dto.getId() == null) {
            throw new IllegalStateException("DTO has no ID");
        }
        return loadFile(dto.getId());
    }

    public FileContentDTO getFileContent(Long id, String keycloakId) {
        Entity entity = getEntityById(id, keycloakId);
        return getFileContent(entity);
    }

    public FileContentDTO getFileContentBySecret(Long id, String secret) {
        Entity entity = getEntityByIdAndSecret(id, secret);
        return getFileContent(entity);
    }


    public String getFileContentForAnalyzing(Entity entity) {
        ToolConfigDataType type = ToolConfigDataType.fromMimeType(entity.getContentType());
        String content = null;
        switch (type) {
            case HTML:
                content = fileStoreAO.loadFileAsString(entity.getLargeObjectId());
                content = content.replaceAll("<[^>]+>", "");
                if (content.length() > 1000) {
                    return content.substring(0, 1000) + "... (truncated)";
                } else {
                    return content;
                }
            case CSV, TSV, JSON: {
                FileProfile profile = getFileStatistics(entity);
                ObjectMapper mapper = new ObjectMapper();
                try {
                    return mapper.writeValueAsString(profile);
                } catch (Exception e) {
                    Log.errorf("Failed to serialize sample data: %s", e.getMessage(), e);
                    return "Error generating preview";
                }
            }
            case TEXT, STRING: {
                content = fileStoreAO.loadFileAsString(entity.getLargeObjectId());
                if (content.length() > 1000) {
                    return content.substring(0, 1000) + "... (truncated)";
                } else {
                    return content;
                }
            }
            default:
                return "File preview not available";
        }
    }

    public FileContentDTO getFileContent(Entity entity) {
        FileContentDTO content = new FileContentDTO();
        ToolConfigDataType type = ToolConfigDataType.fromMimeType(entity.getContentType());
        content.setType(type);
        if (type == ToolConfigDataType.IMAGE) {
            String fileBase64 = fileStoreAO.loadFileAsBase64(entity.getLargeObjectId());
            content.setContent(fileBase64);
        } else {
            String fileString = fileStoreAO.loadFileAsString(entity.getLargeObjectId());
            content.setContent(fileString);
        }
        return content;
    }


    public FileProfile getFileStatistics(Long id, String keycloakId) {
        Entity entity = getEntityById(id, keycloakId);
        return getFileStatistics(entity);
    }

    public FileProfile getFileStatisticsBySecret(Long id, String secret) {
        Entity entity = getEntityByIdAndSecret(id, secret);
        return getFileStatistics(entity);
    }

    public FileProfile getFileStatistics(Entity entity) {
        String f = fileStoreAO.loadFileAsString(entity.getLargeObjectId());
        ToolConfigDataType type = ToolConfigDataType.fromMimeType(entity.getContentType());
        FileProfile profile = new FileProfile(entity.getFileName(), 0L, List.of(), List.of());
        if (type.equals(ToolConfigDataType.JSON)) {
            profile = fileAnalytics.profileJson(f);
        }
        if (type.equals(ToolConfigDataType.TSV) || type.equals(ToolConfigDataType.CSV)) {
            profile = fileAnalytics.profile(f, type);
        }
        return profile;
    }

    public String getSecret() {
        return RandomStringUtils.secureStrong().nextAlphabetic(32);
    }

    public void renewSecret(Entity file) {
        if (file.getId() == null) {
            return;
        }
        boolean dayOld = file.getUpdatedAt().before(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
        if (dayOld || file.getSecret() == null) {
            String secret = getSecret();
            file.setUpdatedAt(new Date());
            file.setSecret(secret);
            ao.persist(file);
        }

    }

}

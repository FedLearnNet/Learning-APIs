package de.unihamburg.daibetes.api.model.sub.file;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.FileResult;
import bio.cosy.feddb.core.api.model.ModelSubFileDTO;
import bio.cosy.feddb.core.api.model.result.ModelExperimentResultDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.BaseFileEntity;
import bio.cosy.feddb.core.helper.FileHelper;
import de.unihamburg.daibetes.api.file.FileBO;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.io.File;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;


@ApplicationScoped
public class ModelSubFileBO extends BaseBo<ModelSubFileDTO, ModelSubFileEntity, ModelSubFileAO, ModelSubFileMapper> {

    @Inject
    FileBO fileBO;

    public void create(File file, Long modelSubId, String path, String keycloakId) {
        if (file == null || modelSubId == null) {
            return;
        }
        String originalFileName = path;
        if (path == null || path.isBlank()) {
            originalFileName = file.getName();
        } else if (path.contains("/")) {
            originalFileName = path.split("/")[path.split("/").length - 1];
        }
        FileDTO internalFile = fileBO.create(file, originalFileName, keycloakId);
        ModelSubFileDTO dto = new ModelSubFileDTO();
        dto.setFile(internalFile);
        dto.setModelSubId(modelSubId);
        this.create(dto);
    }

    public void create(ModelExperimentResultDTO result, Long modelSubId, String keycloakId) {
        result.getFiles()
                .forEach(file -> create(file.filePath().toFile(),
                        modelSubId,
                        file.fileName(),
                        keycloakId));
    }


    public FileResult loadFile(Long id, String keycloakId) {
        ModelSubFileEntity subFile = ao.findByIdOptional(id).orElseThrow(() -> new NotFoundException("File not found"));
        return fileBO.loadFileResult(subFile.getFile());
    }


    @Override
    public void deleteById(Long id) {
        ModelSubFileEntity subFile = ao.findByIdOptional(id).orElseThrow(() -> new NotFoundException("File not found"));
        ao.delete(subFile);
    }

    public byte[] getFilesForPipeline(ModelSubEntity subEntity) {
        Map<BaseFileEntity, File> fileMap = subEntity.getFiles().stream()
                .map(ModelSubFileEntity::getFile)
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        Function.identity(),
                        fileBO::loadFile
                ));
        return FileHelper.filesToZip(fileMap);
    }
}

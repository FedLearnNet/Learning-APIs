package bio.cosy.feddb.local.api.importer.extract;

import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.core.base.BaseFileStoreAO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesAO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesEntity;
import bio.cosy.feddb.local.api.importer.files.upload.OpaquePathUploadValidator;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves app-based extractor {@code inputData} file ids into volume-ready files.
 *
 * <p>Only cohort-scoped connector files marked {@link FileParsingType#OPAQUE} (or otherwise
 * allowlisted by extension after opaque validation rules) are accepted. Volume object names are
 * derived from the tool variable name so the container never sees user-controlled paths.</p>
 */
@ApplicationScoped
public class AppExtractorInputStaging {

    @Inject
    ConnectorFilesAO filesAO;

    @Inject
    BaseFileStoreAO fileStoreAO;

    public record StagedInputs(
            LinkedHashMap<String, String> volumePaths,
            LinkedHashMap<String, File> filesByVolumeName
    ) {
    }

    /**
     * Must run in a transaction: PostgreSQL large-object reads are
     * {@link jakarta.transaction.Transactional.TxType#MANDATORY}.
     */
    @Transactional
    public StagedInputs stage(Long cohortId, Map<String, Object> inputData) {
        LinkedHashMap<String, String> volumePaths = new LinkedHashMap<>();
        LinkedHashMap<String, File> filesByVolumeName = new LinkedHashMap<>();

        if (inputData == null || inputData.isEmpty()) {
            return new StagedInputs(volumePaths, filesByVolumeName);
        }
        if (cohortId == null) {
            throw new BadRequestException("cohortId is required to resolve extractor PATH inputs");
        }

        for (Map.Entry<String, Object> entry : inputData.entrySet()) {
            String variable = entry.getKey();
            if (variable == null || variable.isBlank()) {
                throw new BadRequestException("Extractor input variable name must not be blank");
            }
            Long fileId = toFileId(entry.getValue(), variable);
            ConnectorFilesEntity entity = filesAO.getFileByCohortId(cohortId, fileId)
                    .orElseThrow(() -> new NotFoundException(
                            "Extractor input '" + variable + "' file " + fileId
                                    + " was not found in cohort " + cohortId));

            requireSafeOpaqueSource(entity, variable);

            if (entity.getLargeObjectId() == null) {
                throw new BadRequestException(
                        "Extractor input '" + variable + "' has no stored file content");
            }
            File materialised = fileStoreAO.loadFile(entity.getLargeObjectId());
            if (materialised == null || !materialised.isFile()) {
                throw new BadRequestException(
                        "Extractor input '" + variable + "' could not be materialised");
            }

            String volumeName = OpaquePathUploadValidator.volumeObjectName(
                    variable, entity.getFileName());
            if (filesByVolumeName.containsKey(volumeName)) {
                throw new BadRequestException(
                        "Duplicate volume object name '" + volumeName + "' for extractor inputs");
            }

            // LOB materialisation uses download-*.bin; orch/tools key off the volume object name
            // (and often the file's real name), so copy to a temp file that ends in .zip/.xlsx.
            File staged = copyAsVolumeObject(materialised, volumeName);
            volumePaths.put(variable, volumeName);
            filesByVolumeName.put(volumeName, staged);
        }
        return new StagedInputs(volumePaths, filesByVolumeName);
    }

    private static File copyAsVolumeObject(File materialised, String volumeName) {
        try {
            Path dir = Files.createTempDirectory("extractor-input-");
            Path target = dir.resolve(volumeName).normalize();
            if (!target.startsWith(dir)) {
                throw new BadRequestException("Refusing unsafe volume object name '" + volumeName + "'");
            }
            Files.copy(materialised.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
            dir.toFile().deleteOnExit();
            if (!materialised.delete()) {
                materialised.deleteOnExit();
            }
            return target.toFile();
        } catch (IOException e) {
            throw new BadRequestException(
                    "Could not stage extractor input as '" + volumeName + "': " + e.getMessage());
        }
    }

    private static void requireSafeOpaqueSource(ConnectorFilesEntity entity, String variable) {
        FileParsingType type = entity.getUploadSettings() == null
                ? null
                : entity.getUploadSettings().getFileType();
        if (type != FileParsingType.OPAQUE) {
            throw new BadRequestException(
                    "Extractor input '" + variable
                            + "' must be an opaque PATH upload (fileType=OPAQUE), not a tabular import");
        }
        String name = entity.getFileName() == null ? "" : entity.getFileName().toLowerCase();
        if (!(name.endsWith(".xlsx") || name.endsWith(".xlsm") || name.endsWith(".zip")
                || name.endsWith(".csv"))) {
            throw new BadRequestException(
                    "Extractor input '" + variable + "' has a disallowed file name for PATH staging");
        }
    }

    private static Long toFileId(Object raw, String variable) {
        if (raw == null) {
            throw new BadRequestException("Extractor input '" + variable + "' file id is missing");
        }
        if (raw instanceof Number number) {
            long id = number.longValue();
            if (id <= 0) {
                throw new BadRequestException("Extractor input '" + variable + "' file id is invalid");
            }
            return id;
        }
        if (raw instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.isEmpty() || "undefined".equalsIgnoreCase(trimmed) || "null".equalsIgnoreCase(trimmed)) {
                throw new BadRequestException("Extractor input '" + variable + "' file id is missing");
            }
            try {
                long id = Long.parseLong(trimmed);
                if (id <= 0) {
                    throw new BadRequestException("Extractor input '" + variable + "' file id is invalid");
                }
                return id;
            } catch (NumberFormatException e) {
                throw new BadRequestException(
                        "Extractor input '" + variable + "' file id must be numeric");
            }
        }
        throw new BadRequestException(
                "Extractor input '" + variable + "' file id has an unsupported type");
    }
}

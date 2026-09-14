package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.analytics.FileTypeAnalyzer;
import bio.cosy.feddb.core.base.BaseFileBO;
import bio.cosy.feddb.core.helper.FileHelper;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@ApplicationScoped
public class PatientToolRunFileBO extends BaseFileBO<FileDTO, PatientToolRunFileEntity, PatientToolRunFileAO, PatientToolRunFileMapper> {

    static final String ZIP_CONTENT_TYPE = "application/zip";

    @Inject
    FLNetClientConfig config;

    public PatientToolRunFileEntity storeOutputs(PatientToolRunEntity run, Map<String, File> files) {
        byte[] zip = FileHelper.filesToZipByPath(files);
        PatientToolRunFileEntity entity = new PatientToolRunFileEntity();
        entity.setKeycloakId(config.user().systemUserName());
        entity.setRun(run);
        saveFile(entity, new ByteArrayInputStream(zip), outputsFileName(run), (long) zip.length, ZIP_CONTENT_TYPE);
        ao.persist(entity);
        return entity;
    }

    public Optional<PatientToolRunFileEntity> findByRun(Long runId) {
        return ao.findByRunId(runId);
    }

    public List<PatientToolRunOutputDTO> describeOutputs(PatientToolRunFileEntity outputs) {
        File zip = loadFile(outputs);
        try (ZipFile zipFile = new ZipFile(zip)) {
            List<PatientToolRunOutputDTO> described = new ArrayList<>();
            for (ZipEntry entry : Collections.list(zipFile.entries())) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                try (InputStream in = zipFile.getInputStream(entry)) {
                    described.add(new PatientToolRunOutputDTO(FilenameUtils.removeExtension(name), name,
                            FileTypeAnalyzer.analyseDataType(in, name), entry.getSize()));
                }
            }
            return described;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the stored outputs " + outputs.getId(), e);
        } finally {
            FileUtils.deleteQuietly(zip);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<Path> extractOutputs(Long runId) {
        PatientToolRunFileEntity outputs = findByRun(runId)
                .orElseThrow(() -> new IllegalStateException("Patient tool run " + runId + " finished without outputs"));
        File zip = loadFile(outputs);
        try (InputStream in = Files.newInputStream(zip.toPath())) {
            return FileHelper.unzip(in, Files.createTempDirectory("patient-tool-run-" + runId + "-"))
                    .stream().map(File::toPath).toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not unpack the outputs of patient tool run " + runId, e);
        } finally {
            FileUtils.deleteQuietly(zip);
        }
    }

    public void deleteAllForRuns(List<Long> runIds) {
        for (PatientToolRunFileEntity file : ao.findByRunIds(runIds)) {
            if (file.getLargeObjectId() != null) {
                deleteStoredFile(file.getLargeObjectId());
            }
        }
        ao.deleteByRunIds(runIds);
    }

    private static String outputsFileName(PatientToolRunEntity run) {
        return run.getCohort() != null
                ? "cohort_" + run.getCohort().getId() + "_export_" + run.getId() + ".zip"
                : "patient_tool_run_" + run.getId() + ".zip";
    }
}

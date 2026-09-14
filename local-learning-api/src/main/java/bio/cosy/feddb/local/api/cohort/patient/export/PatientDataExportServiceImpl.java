package bio.cosy.feddb.local.api.cohort.patient.export;

import bio.cosy.feddb.core.api.file.FileResult;
import bio.cosy.feddb.core.api.project.PatientDataExportConfigDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import bio.cosy.feddb.core.helper.FileHelper;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunBO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunDTO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunMapper;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunProgressDTO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunStatusDTO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunSummaryDTO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunType;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.File;
import java.nio.file.Path;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PatientDataExportServiceImpl implements PatientDataExportService {

    @Inject
    PatientDataExportBO exportBO;

    @Inject
    PatientToolRunBO toolRunBO;

    @Inject
    PatientToolRunMapper toolRunMapper;

    @Override
    public Response exportPatientData(Long cohortId, boolean downloadFiles, PatientDataExportConfigDTO config) {
        if (config.isAppBased()) {
            List<Path> exported = exportBO.exportPatientDataTool(cohortId, config);
            if (downloadFiles) {
                Map<String, File> files = new LinkedHashMap<>();
                exported.forEach(path -> files.putIfAbsent(path.getFileName().toString(), path.toFile()));
                return zipResponse(FileHelper.filesToZipByPath(files), "cohort_" + cohortId + "_export.zip");
            }
            return Response.ok(exported.stream().map(path -> path.getFileName().toString()).toList()).build();
        }
        List<Map<String, Object>> exported = exportBO.exportPatientData(cohortId, config);
        if (downloadFiles) {
            StreamingOutput stream = PatientDataExportHelper.convertToCSVStream(exported);
            String filename = "cohort_" + cohortId + "_data.csv";
            return Response.ok(stream)
                    .type("text/csv; charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build();
        } else {
            return Response.ok(exported).build();
        }
    }

    @Override
    public Multi<PatientToolRunProgressDTO> streamAppExport(Long cohortId, PatientDataExportConfigDTO config) {
        // Checked up front so a missing app is still a plain 400 rather than a stream event
        exportBO.requireAppExportConfig(config);
        // Reading the patient data and starting the app block, so both happen on a worker thread
        // once the stream is subscribed rather than on the IO thread
        Multi<PatientToolRunProgressDTO> run = Multi.createFrom().<PatientToolRunProgressDTO>deferred(() -> {
                    PatientToolRunDTO started = exportBO.startAppExport(cohortId, config);
                    return toolRunBO.progress(started.getId());
                })
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                // The stream is already open, so a failure is reported as its terminal event
                .onFailure().recoverWithItem(failure -> serverEvent(RunStatusTypes.ERROR,
                        failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
        // Sent right away: reading a large cohort takes a moment before the run exists
        return Multi.createBy().concatenating().streams(
                Multi.createFrom().item(() -> serverEvent(RunStatusTypes.PENDING, "Preparing the cohort's patient data")),
                run);
    }

    @Override
    @Transactional
    public List<PatientToolRunSummaryDTO> listAppExports(Long cohortId) {
        return toolRunBO.listRuns(cohortId, PatientToolRunType.EXPORT);
    }

    private PatientToolRunProgressDTO serverEvent(RunStatusTypes status, String message) {
        RunMessageLogDTO log = new RunMessageLogDTO();
        log.setProcess(PatientToolRunBO.SERVER_PROCESS);
        log.setMessage(message);
        log.setSeverity(status == RunStatusTypes.ERROR ? "ERROR" : "INFO");
        log.setCreatedAt(new Date());
        PatientToolRunStatusDTO runStatus = new PatientToolRunStatusDTO();
        runStatus.setRunStatus(status);
        runStatus.setLastError(status == RunStatusTypes.ERROR ? message : null);
        return toolRunMapper.toProgressDto(null, runStatus, List.of(log), List.of(), false);
    }

    @Override
    @Transactional
    public Response downloadAppExport(Long cohortId, Long runId) {
        FileResult outputs = toolRunBO.loadOutputs(cohortId, runId);
        File zip = outputs.file();
        return Response.ok(zip, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + outputs.fileName() + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, zip.length())
                .header("Access-Control-Expose-Headers", "Content-Disposition, Content-Length, Content-Type")
                .build();
    }

    private static Response zipResponse(byte[] zip, String filename) {
        return Response.ok(zip, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, zip.length)
                .header("Access-Control-Expose-Headers", "Content-Disposition, Content-Length, Content-Type")
                .build();
    }

    @Override
    public Response exportPatientData(Long cohortId, Long patientId, boolean downloadFiles, PatientDataExportConfigDTO config) {
        List<Map<String, Object>> exported = exportBO.exportDataForPatient(cohortId, patientId, config);
        if (downloadFiles) {
            StreamingOutput stream = PatientDataExportHelper.convertToCSVStream(exported);
            String filename = "patient_" + patientId + "_data.csv";
            return Response.ok(stream)
                    .type("text/csv; charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build();
        } else {
            return Response.ok(exported).build();
        }
    }

    @Override
    public Response exportProjectData(Long cohortId, Long projectId, boolean downloadFiles, PatientDataExportConfigDTO config) {
        List<Map<String, Object>> exported = exportBO.exportDataForProject(cohortId, projectId, config);
        if (downloadFiles) {
            StreamingOutput stream = PatientDataExportHelper.convertToCSVStream(exported);
            String filename = "patient_" + projectId + "_data.csv";
            return Response.ok(stream)
                    .type("text/csv; charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build();
        } else {
            return Response.ok(exported).build();
        }
    }
}

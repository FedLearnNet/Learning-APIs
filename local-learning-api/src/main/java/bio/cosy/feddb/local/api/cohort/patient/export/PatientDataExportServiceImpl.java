package bio.cosy.feddb.local.api.cohort.patient.export;

import bio.cosy.feddb.core.api.project.PatientDataExportConfigDTO;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.core.helper.FileHelper;
import bio.cosy.feddb.local.api.cohort.member.CohortPermissionChecker;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolBO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunType;
import bio.cosy.feddb.core.execution.failure.ExecutionFailure;
import bio.cosy.feddb.core.execution.failure.FailureCategory;
import bio.cosy.feddb.local.execution.run.ExecutionRunBO;
import bio.cosy.feddb.local.execution.run.ExecutionRunDTO;
import io.quarkus.security.PermissionsAllowed;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;

import java.io.File;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PatientDataExportServiceImpl implements PatientDataExportService {

    @Inject
    PatientDataExportBO exportBO;

    @Inject
    PatientToolBO toolBO;

    @Inject
    ExecutionRunBO runBO;

    // The worker reads the cohort; an app-based export then frees it while the app runs.
    @Override
    @Blocking
    @PermissionsAllowed(CohortPermissionChecker.READ)
    public Uni<Response> exportPatientData(Long cohortId, boolean downloadFiles, PatientDataExportConfigDTO config) {
        if (config.isAppBased()) {
            return exportBO.exportPatientDataTool(cohortId, config).map(exported -> appExportResponse(cohortId, downloadFiles, exported));
        }
        List<Map<String, Object>> exported = exportBO.exportPatientData(cohortId, config);
        if (downloadFiles) {
            StreamingOutput stream = PatientDataExportHelper.convertToCSVStream(exported);
            String filename = "cohort_" + cohortId + "_data.csv";
            return Uni.createFrom().item(Response.ok(stream)
                    .type("text/csv; charset=UTF-8")
                    .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                    .build());
        }
        return Uni.createFrom().item(Response.ok(exported).build());
    }

    private Response appExportResponse(Long cohortId, boolean downloadFiles, List<Path> exported) {
        if (downloadFiles) {
            Map<String, File> files = new LinkedHashMap<>();
            exported.forEach(path -> files.putIfAbsent(path.getFileName().toString(), path.toFile()));
            return zipResponse(FileHelper.filesToZipByPath(files), "cohort_" + cohortId + "_export.zip");
        }
        return Response.ok(exported.stream().map(path -> path.getFileName().toString()).toList()).build();
    }

    @Override
    @PermissionsAllowed(CohortPermissionChecker.READ)
    public Multi<ExecutionRunDTO> streamAppExport(Long cohortId, PatientDataExportConfigDTO config) {
        // Checked up front so a missing app is still a plain 400 rather than a stream event
        exportBO.requireAppExportConfig(config);
        // Reading the patient data and starting the app block, so both happen on a worker thread
        // once the stream is subscribed rather than on the IO thread
        Multi<ExecutionRunDTO> run = Multi.createFrom().<ExecutionRunDTO>deferred(() -> {
                    ExecutionRunDTO started = exportBO.startAppExport(cohortId, config);
                    return runBO.subscribe(started.getId());
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
    @PermissionsAllowed(CohortPermissionChecker.READ)
    public List<ExecutionRunDTO> listAppExports(Long cohortId) {
        return toolBO.listTransactional(cohortId, PatientToolRunType.EXPORT);
    }

    // What the server does before the run exists, as a run without id.
    private ExecutionRunDTO serverEvent(RunStatusTypes status, String message) {
        ExecutionRunDTO run = new ExecutionRunDTO();
        run.setName(message);
        run.setStatus(status);
        if (status == RunStatusTypes.ERROR) {
            run.setFailure(new ExecutionFailure(FailureCategory.INFRASTRUCTURE, message));
        }
        return run;
    }

    @Override
    @PermissionsAllowed(CohortPermissionChecker.READ)
    public Response downloadAppExport(Long cohortId, Long runId) {
        return zipResponse(toolBO.zipOutputsTransactional(cohortId, runId), "cohort_" + cohortId + "_export_" + runId + ".zip");
    }

    private Response zipResponse(byte[] zip, String filename) {
        return Response.ok(zip, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, zip.length)
                .header("Access-Control-Expose-Headers", "Content-Disposition, Content-Length, Content-Type")
                .build();
    }

    @Override
    @PermissionsAllowed(CohortPermissionChecker.READ)
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
    @PermissionsAllowed(CohortPermissionChecker.READ)
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

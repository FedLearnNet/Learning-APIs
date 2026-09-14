package bio.cosy.feddb.local.api.cohort.patient.export;

import bio.cosy.feddb.core.api.project.PatientDataExportConfigDTO;
import bio.cosy.feddb.core.api.project.PatientDataPivotJoinField;
import bio.cosy.feddb.core.api.project.PatientDataPivotDuplicatePolicy;
import bio.cosy.feddb.local.api.cohort.patient.PatientService;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunProgressDTO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunSummaryDTO;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.resteasy.reactive.RestStreamElementType;

import java.util.List;

@Path(PatientDataExportService.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "PatientExport", description = "Service for managing Patients")
public interface PatientDataExportService {
    String PATH = PatientService.PATH + "/export";


    @POST
    @Produces("text/csv")
    @Operation(
            summary = "Export patient data as CSV",
            description = "Returns a UTF-8 CSV file for the given patient. Columns: id, patientId, ontologyId, value, visitId, visitTimestamp, visitTimestampFormat. " +
                    "The CSV includes a header row and one row per data entry. " +
                    "An app-based export runs the configured export app and returns its outputs as a zip once it has finished."
    )
    @APIResponse(
            responseCode = "200",
            description = "CSV file containing the patient's data",
            content = @Content(
                    mediaType = "text/csv",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")
            )
    )
    @APIResponse(responseCode = "400", description = "Invalid patientId (must be a positive number)")
    @APIResponse(responseCode = "404", description = "Patient or patient data not found")
    @APIResponse(responseCode = "500", description = "Internal server error while generating CSV")
    Response exportPatientData(
            @PathParam("cohortId") Long cohortId,
            @DefaultValue("true")
            @QueryParam("downloadFiles") boolean downloadFiles,
            @Valid PatientDataExportConfigDTO config
    );

    @POST
    @Path("app/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.APPLICATION_JSON)
    @Operation(
            summary = "Run an app-based cohort export",
            description = "Starts the export app configured in the export config on the cohort's patient data and streams " +
                    "the run's progress as Server-Sent Events. The last event has a terminal status; when the run " +
                    "finished, its outputs can be downloaded as a zip from app/{runId}/download."
    )
    @APIResponse(responseCode = "200", description = "SSE stream of progress events",
            content = @Content(mediaType = MediaType.SERVER_SENT_EVENTS,
                    schema = @Schema(implementation = PatientToolRunProgressDTO.class)))
    @APIResponse(responseCode = "400", description = "No app selected or no patient data to export")
    Multi<PatientToolRunProgressDTO> streamAppExport(
            @PathParam("cohortId") Long cohortId,
            @Valid PatientDataExportConfigDTO config);

    @GET
    @Path("app")
    @Operation(
            summary = "List the app-based exports of a cohort",
            description = "The latest app-based export runs of the cohort, newest first, with their stored outputs."
    )
    @APIResponse(responseCode = "200", description = "Past app-based exports")
    List<PatientToolRunSummaryDTO> listAppExports(@PathParam("cohortId") Long cohortId);

    @GET
    @Path("app/{runId}/download")
    @Produces("application/zip")
    @Operation(
            summary = "Download the outputs of an app-based export",
            description = "Returns every output of a finished app-based export run as a zip. The zip is stored with " +
                    "the run and can be downloaded again."
    )
    @APIResponse(responseCode = "200", description = "Zip with the app's outputs",
            content = @Content(mediaType = "application/zip",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")))
    @APIResponse(responseCode = "404", description = "Run unknown for this cohort or not finished yet")
    Response downloadAppExport(
            @PathParam("cohortId") Long cohortId,
            @PathParam("runId") Long runId);

    @POST
    @Path("{patientId}")
    @Produces("text/csv")
    @Operation(
            summary = "Export patient data as CSV",
            description = "Returns a UTF-8 CSV file for the given patient. Columns: id, patientId, ontologyId, value, visitId, visitTimestamp, visitTimestampFormat. " +
                    "The CSV includes a header row and one row per data entry."
    )
    @APIResponse(
            responseCode = "200",
            description = "CSV file containing the patient's data",
            content = @Content(
                    mediaType = "text/csv",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")
            )
    )
    @APIResponse(responseCode = "400", description = "Invalid patientId (must be a positive number)")
    @APIResponse(responseCode = "404", description = "Patient or patient data not found")
    @APIResponse(responseCode = "500", description = "Internal server error while generating CSV")
    Response exportPatientData(
            @PathParam("cohortId") Long cohortId,
            @PathParam("patientId") Long patientId,
            @DefaultValue("true")
            @QueryParam("downloadFiles") boolean downloadFiles,
            @Valid PatientDataExportConfigDTO config);

    @POST
    @Path("project/{projectId}")
    @Produces("text/csv")
    @Operation(
            summary = "Export project patient data as CSV",
            description = "Returns a UTF-8 CSV file for all patients in the given project. Columns: id, patientId, ontologyId, value, visitId, visitTimestamp, visitTimestampFormat. " +
                    "The CSV includes a header row and one row per data entry across all patients of the project."
    )
    @APIResponse(
            responseCode = "200",
            description = "CSV file containing all patient data for the project",
            content = @Content(
                    mediaType = "text/csv",
                    schema = @Schema(type = SchemaType.STRING, format = "binary")
            )
    )
    @APIResponse(responseCode = "400", description = "Invalid projectId (must be a positive number)")
    @APIResponse(responseCode = "404", description = "Project or project data not found")
    @APIResponse(responseCode = "500", description = "Internal server error while generating CSV")
    Response exportProjectData(
            @PathParam("cohortId") Long cohortId,
            @PathParam("projectId") Long projectId,
            @DefaultValue("true")
            @QueryParam("downloadFiles") boolean downloadFiles,
            @Valid PatientDataExportConfigDTO config);

}

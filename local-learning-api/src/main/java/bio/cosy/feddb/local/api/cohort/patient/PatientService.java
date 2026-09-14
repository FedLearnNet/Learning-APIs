package bio.cosy.feddb.local.api.cohort.patient;

import io.quarkus.security.Authenticated;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.groups.ConvertGroup;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.core.base.ValidationGroups;
import bio.cosy.feddb.local.api.cohort.CohortService;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.jboss.resteasy.reactive.ResponseStatus;

import static org.jboss.resteasy.reactive.RestResponse.StatusCode.CREATED;

@Path(PatientService.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "Patient", description = "Service for managing Patients")
public interface PatientService {
    static final String PATH = CohortService.PATH + "/{cohortId}/data/patient";
    // Bulk addition of patients is handled in the CohortServices as the bulk addition
    // is always per one cohort.

    @GET
    @Path("/reduced")
    @Operation(summary = "Get reduced patient data for cohort with pagination",
               description = "Returns patient data with time-series reduced to single values per schema node using the specified reduction methods. " +
                           "Each patient will have at most one data entry per schema node. " +
                           "Results are paginated using page and page_size query parameters. " +
                           "Numeric reduction methods: latest, earliest, average, median, max, min, count. " +
                           "Non-numeric reduction methods: latest, earliest, count. " +
                           "When no visitTimestamp is associated, one entry is selected randomly.")
    @APIResponse(responseCode = "200", description = "Paginated list of patients with reduced time-series data",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PagedResponse.class)))
    @APIResponse(responseCode = "400", description = "Invalid reduction method or pagination parameters. " +
                "Numeric methods: latest, earliest, average, median, max, min, count. " +
                "Non-numeric methods: latest, earliest, count. " +
                "Page must be >= 1, page_size must be >= 1 and <= 1000")
    @APIResponse(responseCode = "500", description = "Internal server error due to invalid data state (e.g., missing schema node or data type information)")
    PagedResponse<PatientDTO> getReducedPatientData(
            @PathParam("cohortId") Long cohortId,
            @QueryParam("numericReduction")
            @DefaultValue("latest")
            String numericReductionMethod,
            @QueryParam("nonNumericReduction")
            @DefaultValue("latest")
            String nonNumericReductionMethod,
            @QueryParam("page")
            @DefaultValue("1")
            Integer page,
            @QueryParam("page_size")
            @DefaultValue("50")
            Integer pageSize);
                /*
                * Examples:
                *
                * 1. Default behavior (latest for both, first page of 50):
                *    GET /cohorts/123/data/patient/reduced
                *
                * 2. Average numeric values, latest non-numeric, first page:
                *    GET /cohorts/123/data/patient/reduced?numericReduction=average&nonNumericReduction=latest
                *
                * 3. Maximum numeric values, count non-numeric entries, page 2 with 10 items:
                *    GET /cohorts/123/data/patient/reduced?numericReduction=max&nonNumericReduction=count&page=2&page_size=10
                *
                * Timestamp Ordering Logic:
                *
                * 1. Primary ordering: visitTimestamp (if not null)
                * 2. Random selection: when multiple entries have identical or null visitTimestamp
                * */


    @GET
    @Path("/{internalPatientId}")
    @Operation(summary = "Get patient data by internal ID", description = "Returns patient data for a specific internal patient ID in the cohort")
    @APIResponse(responseCode = "200", description = "Patient data for the specified internal patient ID",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PatientDTO.class)))
    @APIResponse(responseCode = "404", description = "Patient with the specified internal ID does not exist in the cohort",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = String.class)))
    @APIResponse(responseCode = "400", description = "Invalid input data",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = String.class)))
    PatientDTO getPatientDataById(@PathParam("cohortId") Long cohortId, @PathParam("internalPatientId") Long internalPatientId);

    @GET
    @Path("/by-external-id/{externalPatientId}")
    @Operation(summary = "Get patient data by external ID", description = "Returns patient data for a specific external patient ID in the cohort")
    @APIResponse(responseCode = "200", description = "Patient data for the specified external patient ID",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PatientDTO.class)))
    @APIResponse(responseCode = "404", description = "Patient with the specified external ID does not exist in the cohort",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = String.class)))
    @APIResponse(responseCode = "400", description = "Invalid input data",
            content = @Content(mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = String.class)))
    PatientDTO getPatientDataByExternalId(@PathParam("cohortId") Long cohortId, @PathParam("externalPatientId") String externalPatientId);

    @GET
    @Operation(summary = "List all patient data for a cohort", description = "Returns a list of all patient data records for the specified cohort")
    @APIResponse(responseCode = "200", description = "List of all patient data for the cohort",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PatientDTO.class, type = SchemaType.ARRAY)))
    List<PatientDTO> listPatientData(@PathParam("cohortId") Long cohortId);

    @GET
    @Path("/references")
    @Operation(summary = "List patient identifiers of a cohort",
            description = "Returns the internal and external id of the cohort's patients, ordered by internal id and "
                    + "capped at the given limit. Meant for pickers such as the export patient filter, which only need "
                    + "to name patients.")
    @APIResponse(responseCode = "200", description = "Patient identifiers of the cohort",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PatientReferenceDTO.class, type = SchemaType.ARRAY)))
    List<PatientReferenceDTO> listPatientReferences(
            @PathParam("cohortId") Long cohortId,
            @QueryParam("limit") @DefaultValue("1000") @Min(1) @Max(10000) Integer limit);

    @POST
    @Operation(summary = "Create a new patient entry", description = "Creates a new patient data record. If the patient already exists in the cohort, an error is returned. Use PATCH to add new data entries to an existing patient.")
    @APIResponse(responseCode = "201", description = "Patient data created successfully",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PatientDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid input data")
    @APIResponse(responseCode = "409", description = "Patient with the same external ID already exists in the cohort")
    @Transactional
    @ResponseStatus(CREATED)
    PatientDTO createPatient(@PathParam("cohortId") Long cohortId, @Valid @ConvertGroup(to = ValidationGroups.Post.class) PatientDTO createPatientDataDTO);

    @DELETE
    @Path("/{internalPatientId}")
    @Operation(summary = "Delete patient data by internal ID", description = "Deletes all patient data records for the specified internal patient ID in the cohort, but keep the traceability data")
    @APIResponse(responseCode = "200", description = "Patient data deleted successfully")
    @Transactional
    Response deletePatientData(@PathParam("cohortId") Long cohortId, @PathParam("internalPatientId") Long internalPatientId);
        // Careful here, we delete the patient data but NOT tracability data!
        // So we keep the PatientMetaEntity but delete all related PatientDataEntity records

    @PUT
    @Path("/{internalPatientId}/rollback/{revisionNumber}")
    @Operation(summary = "Rollback patient data to a specific audit revision",
               description = "Restores all data entries of a patient to the state at the given audit revision number. " +
                       "If delete_audit is true, audit entries at and after the target revision are permanently deleted.")
    @APIResponse(responseCode = "200", description = "Patient data rolled back successfully")
    @APIResponse(responseCode = "404", description = "Patient not found")
    @Transactional
    PatientDTO rollbackPatientData(@PathParam("cohortId") Long cohortId,
                                 @PathParam("internalPatientId") Long internalPatientId,
                                 @PathParam("revisionNumber") Integer revisionNumber,
                                 @QueryParam("delete_audit") @DefaultValue("false") boolean deleteAudit);
}

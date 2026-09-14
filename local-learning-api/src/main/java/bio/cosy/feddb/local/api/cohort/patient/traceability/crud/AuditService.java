package bio.cosy.feddb.local.api.cohort.patient.traceability.crud;

import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.core.base.sort.SortDirectionEnum;
import bio.cosy.feddb.local.api.cohort.CohortService;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;


@Path(AuditService.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "PatientAuditService", description = "Service for managing Patient  Audits Services")
public interface AuditService {
    String PATH = CohortService.PATH + "/logs";

    @GET
    @Operation(summary = "List all PatientDataTraceabilityLogs",
            description = """
                    Returns a paginated, filterable list of PatientDataTraceabilityLog records.
                    
                    Search capabilities:
                    - EXTERNAL_PATIENT_ID: Search by patient ID (string)
                    - KEYCLOAK_ID: Search by user ID (string)
                    - CONNECTOR_ID: Search by connector ID (integer)
                    - RUN_ID: Search by run ID (integer)
                    - REVISION_TYPE: Search by change type (ADD, MOD, DEL)
                    
                    Sort capabilities:
                    - All searchable fields plus REVISION_NUMBER and REVISION_TIMESTAMP
                    
                    Multiple search criteria are supported using comma-separated values.
                    All search criteria are combined with AND logic.
                    """)
    @APIResponse(responseCode = "200", description = "List of PatientDataTraceabilityLogs")
    @APIResponse(responseCode = "400", description = "Invalid request parameters (pagination, search criteria, or sort parameters)")
    @APIResponse(responseCode = "404", description = "Cohort not found")
    PagedResponse<PatientDataTraceabilityLogDto> list(
            @QueryParam("cohortId") Long cohortId,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("sort") @DefaultValue("REVISION_TIMESTAMP") AuditFieldEnum sortField,
            @QueryParam("direction") @DefaultValue("DESC") SortDirectionEnum sortDirection,
            @QueryParam("search_terms") List<String> searchTerms,
            @QueryParam("search_fields") List<AuditFieldEnum> searchFields);


    @GET
    @Path("/patient/{patientId}/rev/{revId}")

    @Operation(summary = "List all PatientDataTraceabilityLogs",
            description = """
                    Returns a PatientDataTraceabilityDetailLogDto including records for a specific patient revision.
                    """)
    @APIResponse(responseCode = "200", description = "List of Changes for a specific patient revision")
    @APIResponse(responseCode = "400", description = "Invalid request parameters")
    @APIResponse(responseCode = "404", description = "Rev not found")
    PatientDataTraceabilityDetailLogDto getPatientChangesDetail(@PathParam("patientId") Long patientId,
                                                                @PathParam("revId") Integer revId);
}


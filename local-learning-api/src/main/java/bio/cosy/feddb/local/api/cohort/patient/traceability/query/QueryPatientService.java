package bio.cosy.feddb.local.api.cohort.patient.traceability.query;

import bio.cosy.feddb.core.base.PagedResponse;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/patients/query")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "PatientService", description = "Service for managing Patient Services")
public interface QueryPatientService {
    @GET
    @Operation(summary = "List all QueryPatient",
            description = "Returns a paginated, filterable list of QueryPatient records")
    @APIResponse(responseCode = "200", description = "List of QueryPatient")
    PagedResponse<QueryPatientDTO> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("cohort_id") Long internalCohortId,
            @QueryParam("patient_id") Long internalPatientId,
            @QueryParam("query_id") Long queryId);
}

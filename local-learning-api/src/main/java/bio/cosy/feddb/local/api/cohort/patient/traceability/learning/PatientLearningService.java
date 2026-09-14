package bio.cosy.feddb.local.api.cohort.patient.traceability.learning;

import bio.cosy.feddb.core.base.PagedResponse;
import io.quarkus.security.Authenticated;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/patients/learning")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
//@RolesAllowed("admin")
@Tag(name = "PatientService", description = "Service for managing Patient Services")
public interface PatientLearningService {


    @GET
    @Operation(summary = "List all PatientLearning",
            description = "Returns a paginated, filterable list of PatientLearning records")
    @APIResponse(responseCode = "200", description = "List of PatientLearning")
    PagedResponse<PatientLearningDTO> list(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @QueryParam("cohort_id") Long internalCohortId,
            @QueryParam("patient_id") Long internalPatientId,
            @QueryParam("request_id") Long requestId);

    @PUT
    @Path("accept")
    @Operation(summary = "Accept patients",
            description = "remove the patient which are not in the list of patients to be accepted")
    @APIResponse(responseCode = "200", description = "Patients accepted")
    @APIResponse(responseCode = "400", description = "Invalid data")
    @APIResponse(responseCode = "404", description = "Request not found")
    Response acceptPatients(List<PatientLearningDTO> update);

}

package bio.cosy.feddb.local.api.cohort.patient.dataentry;

import bio.cosy.feddb.core.base.ValidationGroups;
import bio.cosy.feddb.local.api.cohort.patient.PatientService;
import io.quarkus.security.Authenticated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.groups.ConvertGroup;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.Set;


@Path(PatientDataEntryService.PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Authenticated
@Tag(name = "PatientDataEntry", description = "Service for managing Patient Data Entries. Only BATCH creation and updates are allowed to correctly handle the connection between data entries.")
public interface PatientDataEntryService {
    String PATH = PatientService.PATH + "/{internalPatientId}/dataentry";

    /**
     * Upserts multiple patient data entries
     *
     * <p>Supports mixed payloads: existing entries (with ID) are updated,
     * new entries (without ID) are created.
     */
    @PUT
    @Path("bulk")
    @Operation(summary = "Updates or creates multiple patient data entries",
            description = "Updates existing data entries (if id is provided) or creates new entries (if id is null) for the specified patient in the cohort. This is an upsert operation.")
    @APIResponse(
            responseCode = "200",
            description = "Data entries updated/created successfully",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PatientDataEntryDTO.class, type = SchemaType.ARRAY)
            )
    )
    @APIResponse(responseCode = "400", description = "Invalid input data")
    @APIResponse(responseCode = "404", description = "Data entry, patient, or cohort not found")
    public Response updateDataEntries(
            @PathParam("cohortId") Long cohortId,
            @PathParam("internalPatientId") Long internalPatientId,
            List<@Valid @ConvertGroup(to = ValidationGroups.Upsert.class) PatientDataEntryDTO> updateDTOs);

    @POST
    @Path("bulk")
    @Operation(summary = "Create multiple patient data entries",
            description = "Creates multiple data entries for the specified patient in the cohort in a single transaction. Only one dataentry per schema node is allowed.")
    @APIResponse(responseCode = "201", description = "Data entries created successfully",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PatientDataEntryDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid input data")
    @APIResponse(responseCode = "404", description = "Patient or cohort not found")
    public Response createMultipleDataEntries(
            @PathParam("cohortId") Long cohortId,
            @PathParam("internalPatientId") Long internalPatientId,
            List<@Valid @ConvertGroup(to = ValidationGroups.Post.class) PatientDataEntryDTO> createDTOs);


    @POST
    @Operation(summary = "Create a single patient data entries",
            description = "Creates a single data entries for the specified patient in the cohort in a single transaction. Only one dataentry per schema node is allowed.")
    @APIResponse(responseCode = "201", description = "Data entries created successfully",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PatientDataEntryDTO.class)))
    @APIResponse(responseCode = "400", description = "Invalid input data")
    @APIResponse(responseCode = "404", description = "Patient or cohort not found")
    public Response createSingleDataEntries(
            @PathParam("cohortId") Long cohortId,
            @PathParam("internalPatientId") Long internalPatientId,
            @Valid @ConvertGroup(to = ValidationGroups.Post.class) PatientDataEntryDTO createDTO);

    @PUT
    @Path("{id}")
    @Operation(summary = "Updates single existing patient data entry",
            description = "Updates single data entry for the specified patient in the cohort. Replaces the existing entry with the given data.")
    @APIResponse(
            responseCode = "200",
            description = "Data entries updated successfully",
            content = @Content(
                    mediaType = MediaType.APPLICATION_JSON,
                    schema = @Schema(implementation = PatientDataEntryDTO.class, type = SchemaType.ARRAY)
            )
    )
    @APIResponse(responseCode = "400", description = "Invalid input data")
    @APIResponse(responseCode = "404", description = "Data entry, patient, or cohort not found")
    public Response update(
            @PathParam("cohortId") Long cohortId,
            @PathParam("internalPatientId") Long internalPatientId,
            @PathParam("id") Long id,
            @Valid @ConvertGroup(to = ValidationGroups.Put.class) PatientDataEntryDTO updateDto);

    @DELETE
    @Path("bulk")
    @Operation(summary = "Delete multiple patient data entries",
            description = "Deletes multiple data entries for the specified patient in the cohort in a single transaction")
    @APIResponse(responseCode = "200", description = "Data entries deleted successfully")
    @APIResponse(responseCode = "400", description = "Invalid input data")
    public Response deleteMultipleDataEntries(
            @PathParam("cohortId") Long cohortId,
            @PathParam("internalPatientId") Long internalPatientId,
            Set<@NotNull Long> deleteIds);

    @DELETE
    @Path("{id}")
    @Operation(summary = "Delete single patient data entries",
            description = "Deletes single data entries for the specified patient in the cohort in a single transaction")
    @APIResponse(responseCode = "200", description = "Data entries deleted successfully")
    @APIResponse(responseCode = "400", description = "Invalid input data")
    public Response deleteDataEntry(
            @PathParam("cohortId") Long cohortId,
            @PathParam("internalPatientId") Long internalPatientId,
            @PathParam("id") Long id);
}

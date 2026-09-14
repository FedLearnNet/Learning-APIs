package bio.cosy.feddb.local.api.cohort.patient.dataentry;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class PatientDataEntryServiceImpl implements PatientDataEntryService {
    @Inject
    PatientDataEntryBO patientDataEntryBO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Override
    @Transactional
    public Response updateDataEntries(Long cohortId, Long internalPatientId, List<PatientDataEntryDTO> updateDTOs) {
        cohortMemberAuthBO.checkForEditPatients(cohortId, userIdentity.getKeycloakId());
        try {
            List<PatientDataEntryDTO> updated = patientDataEntryBO.updateDataEntries(cohortId, internalPatientId, updateDTOs);
            return Response.ok(updated).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Data entries update failed: " + e.getMessage()).build();
        }
    }

    @Override
    @Transactional
    public Response createMultipleDataEntries(
            Long cohortId, Long internalPatientId, List<PatientDataEntryDTO> createDTOs) {
        cohortMemberAuthBO.checkForEditPatients(cohortId, userIdentity.getKeycloakId());
        try {
            List<PatientDataEntryDTO> created = patientDataEntryBO.createMultipleDataEntries(cohortId, internalPatientId, createDTOs);
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            // Handle other unexpected errors (including constraint violations)
            return Response.status(Response.Status.BAD_REQUEST).entity("Data entries creation failed: " + e.getMessage()).build();
        }
    }

    @Override
    @Transactional
    public Response createSingleDataEntries(Long cohortId, Long internalPatientId, PatientDataEntryDTO createDTO) {
        cohortMemberAuthBO.checkForEditPatients(cohortId, userIdentity.getKeycloakId());
        try {
            PatientDataEntryDTO created = patientDataEntryBO.createSingleDataEntries(cohortId, internalPatientId, createDTO);
            return Response.status(Response.Status.CREATED).entity(created).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        } catch (Exception e) {
            // Handle other unexpected errors (including constraint violations)
            return Response.status(Response.Status.BAD_REQUEST).entity("Data entries creation failed: " + e.getMessage()).build();
        }
    }

    @Override
    @Transactional
    public Response update(Long cohortId, Long internalPatientId, Long id, PatientDataEntryDTO updateDto) {
        cohortMemberAuthBO.checkForEditPatients(cohortId, userIdentity.getKeycloakId());
        try {
            PatientDataEntryDTO updated = patientDataEntryBO.updateDataEntry(cohortId, internalPatientId, id, updateDto);
            return Response.ok(updated).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND).entity(e.getMessage()).build();
        }
    }

    @Override
    @Transactional
    public Response deleteMultipleDataEntries(Long cohortId, Long internalPatientId, Set<Long> deleteIds) {
        cohortMemberAuthBO.checkForDelete(cohortId, userIdentity.getKeycloakId());
        try {
            patientDataEntryBO.deleteMultipleDataEntries(cohortId, internalPatientId, deleteIds);
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        } catch (NotFoundException e) {
            // We just return 200 OK
        } catch (Exception e) {
            // Handle other unexpected errors
            return Response.status(Response.Status.BAD_REQUEST).entity("Data entries deletion failed: " + e.getMessage()).build();
        }
        return Response.ok().build();
    }

    @Override
    public Response deleteDataEntry(Long cohortId, Long internalPatientId, Long id) {
        return deleteMultipleDataEntries(cohortId, internalPatientId, Set.of(id));
    }
}

package bio.cosy.feddb.local.api.cohort.patient;


import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryRollbackBO;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
public class PatientServiceImpl implements PatientService {

    @Inject
    PatientBO patientBO;

    @Inject
    PatientDataEntryRollbackBO rollbackBO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Override
    public PatientDTO getPatientDataById(Long cohortID, Long internalPatientId) {
        PatientDTO patientData = patientBO.getPatientDataById(cohortID, internalPatientId);
        if (patientData == null) {
            throw new NotFoundException();
        }
        return patientData;
    }

    @Override
    public PatientDTO getPatientDataByExternalId(Long cohortID, String externalPatientId) {
        PatientDTO patientData = patientBO.getPatientDataByExternalId(cohortID, externalPatientId);
        if (patientData == null) {
            throw new NotFoundException();
        }
        return patientData;
    }

    @Override
    public List<PatientDTO> listPatientData(Long cohortId) {
        return patientBO.listPatientData(cohortId);
    }

    @Override
    @Transactional
    public List<PatientReferenceDTO> listPatientReferences(Long cohortId, Integer limit) {
        return patientBO.listPatientReferences(cohortId, limit);
    }

    @Override
    public PagedResponse<PatientDTO> getReducedPatientData(Long cohortId, String numericReductionMethod, String nonNumericReductionMethod, Integer page, Integer pageSize) {
        try {
            // Validate pagination parameters
            if (page < 1) {
                throw new BadRequestException("Page number must be >= 1");
            }
            if (pageSize < 1 || pageSize > 1000) {
                throw new BadRequestException("Page size must be between 1 and 1000");
            }

            return patientBO.getReducedPatientData(cohortId, numericReductionMethod, nonNumericReductionMethod, page, pageSize);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e);
        } catch (IllegalStateException e) {
            throw new InternalServerErrorException(e);
            // Handle internal state issues (like null schema nodes, data types, etc.)
        }
    }

    @Override
    @Transactional
    public PatientDTO createPatient(Long cohortId, PatientDTO createPatientDataDTO) {
        try {
            cohortMemberAuthBO.checkForEditPatients(cohortId, userIdentity.getKeycloakId());
            // Implementation to create a new patient data record
            // This will throw an exception if the patient already exists in the cohort
            return patientBO.createPatientData(cohortId, createPatientDataDTO);
        } catch (IllegalStateException e) {
            throw new ClientErrorException(e.getMessage(), Response.Status.CONFLICT, e);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid input data: " + e.getMessage(), e);
        }
    }

    @Override
    @Transactional
    public Response deletePatientData(Long cohortId, Long internalPatientId) {
        cohortMemberAuthBO.checkForDelete(cohortId, userIdentity.getKeycloakId());
        // Implementation to delete patient data by internal ID
        // Careful here, we delete the patient data but NOT tracability data!
        // So we keep the PatientMetaEntity but delete all related PatientDataEntity records
        patientBO.deletePatientData(cohortId, internalPatientId);
        return Response.ok().build();
    }

    @Override
    @Transactional
    public PatientDTO rollbackPatientData(Long cohortId, Long internalPatientId, Integer revisionNumber, boolean deleteAudit) {
        rollbackBO.rollbackPatientToRevision(internalPatientId, revisionNumber, deleteAudit);
        return patientBO.getPatientDataById(cohortId, internalPatientId);
    }
}

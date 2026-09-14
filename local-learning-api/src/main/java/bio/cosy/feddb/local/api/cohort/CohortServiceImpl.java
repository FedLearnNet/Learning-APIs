package bio.cosy.feddb.local.api.cohort;


import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortAvailableUserDTO;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberCreateDTO;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberBO;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberDTO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
public class CohortServiceImpl implements CohortService {

    @Inject
    CohortBO cohortBO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberBO cohortMemberBO;

    @Override
    @Transactional
    public List<CohortDTO> list() {
        String keycloakId = userIdentity.getKeycloakId();
        return cohortBO.getAll(keycloakId);
    }

    @Override
    @Transactional
    public CohortNameHealthDTO checkNameHealth(String name, Long excludeId) {
        return cohortBO.checkNameHealth(name, excludeId);
    }

    @Override
    @Transactional
    public CohortDetailDTO get(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return cohortBO.findById(id, keycloakId);
    }

    @Override
    @Transactional
    public CohortDTO create(CreateCohortDTO createDTO) {
        try {
            String keycloakId = userIdentity.getKeycloakId();
            return cohortBO.create(createDTO, keycloakId);
        } catch (WebApplicationException e) {
            // Preserve intentional HTTP errors (e.g. 409 name already exists)
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.error("Cohort Creation failed" + e.getMessage(), e);
            throw new BadRequestException("Cohort Creation failed");
        } catch (Exception e) {
            Log.error("Cohort Creation failed" + e.getMessage(), e);
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    @Transactional
    public CohortDetailDTO update(Long id, UpdateCohortDTO updateDTO) {
        try {
            String keycloakId = userIdentity.getKeycloakId();
            return cohortBO.update(id, updateDTO, keycloakId);
        } catch (WebApplicationException e) {
            throw e;
        } catch (IllegalArgumentException | IllegalStateException e) {
            Log.error("Cohort update failed" + e.getMessage(), e);
            throw new BadRequestException("Cohort update failed");
        } catch (Exception e) {
            Log.error("Cohort update failed" + e.getMessage(), e);
            throw new InternalServerErrorException(e.getMessage());
        }
    }

    @Override
    @Transactional
    public CohortMemberDTO addMember(Long id, CohortMemberCreateDTO createDTO) {
        return cohortMemberBO.addMember(createDTO, userIdentity.getKeycloakId());
    }

    @Override
    public List<CohortAvailableUserDTO> getAllUsers() {
        return cohortMemberBO.getAvailableKeycloakUsers();
    }

    @Override
    @Transactional
    public CohortMemberDTO updateMember(Long cohortId, Long id, CohortMemberDTO updateDTO) {
        return cohortMemberBO.updateMember(updateDTO, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public Response deleteMember(Long cohortId, Long id) {
        cohortMemberBO.deleteMember(cohortId, id, userIdentity.getKeycloakId());
        return Response.status(Response.Status.OK).build();
    }

    @Override
    public Response delete(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        try {
            CohortDeletionAcceptedDTO accepted = cohortBO.scheduleDeletion(id, keycloakId);
            return Response.status(Response.Status.ACCEPTED).entity(accepted).build();
        } catch (NotFoundException e) {
            return Response.status(Response.Status.OK).build();
        } catch (WebApplicationException e) {
            throw e;
        } catch (Exception e) {
            Log.error("Cohort deletion failed", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(e.getMessage()).build();
        }
    }
}

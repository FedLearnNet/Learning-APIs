package bio.cosy.feddb.local.api.cohort.queryability;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
public class CohortQueryAbilityServiceImpl implements CohortQueryAbilityService {
    @Inject
    CohortQueryAbilityBO cohortQueryAbilityBO;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Override
    public List<CohortQueryAbilityDTO> list(Long cohortId) {
        return cohortQueryAbilityBO.list(cohortId);
    }

    @Override
    public CohortQueryAbilityDTO retrieve(Long cohortId, Long id) {
        return cohortQueryAbilityBO.findByIds(cohortId, id);
    }

    @Override
    @Transactional
    public Response create(Long cohortId, CreateCohortQueryAbilityDTO createDTO) {
        cohortMemberAuthBO.checkForEdit(cohortId, userIdentity.getKeycloakId());
        CohortQueryAbilityDTO created = cohortQueryAbilityBO.create(cohortId, createDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @Override
    @Transactional
    public Response createBulk(Long cohortId, List<CreateCohortQueryAbilityDTO> createDTO) {
        cohortMemberAuthBO.checkForEdit(cohortId, userIdentity.getKeycloakId());
        List<CohortQueryAbilityDTO> created = cohortQueryAbilityBO.createBulk(cohortId, createDTO);
        return Response.status(Response.Status.CREATED).entity(created).build();
    }

    @Override
    @Transactional
    public CohortQueryAbilityDTO update(Long cohortId, Long id, CohortQueryAbilityDTO updateDTO) {
        cohortMemberAuthBO.checkForEdit(cohortId, userIdentity.getKeycloakId());
        return cohortQueryAbilityBO.update(cohortId, id, updateDTO);
    }

    @Override
    @Transactional
    public List<CohortQueryAbilityDTO> updateBulk(Long cohortId, List<CreateCohortQueryAbilityDTO> dtos) {
        cohortMemberAuthBO.checkForEdit(cohortId, userIdentity.getKeycloakId());
        return cohortQueryAbilityBO.updateBulk(cohortId, dtos);
    }

    @Override
    @Transactional
    public Response delete(Long cohortId, Long id) {
        cohortMemberAuthBO.checkForDelete(cohortId, userIdentity.getKeycloakId());
        cohortQueryAbilityBO.delete(cohortId, id);
        return Response.ok().build();
    }
}

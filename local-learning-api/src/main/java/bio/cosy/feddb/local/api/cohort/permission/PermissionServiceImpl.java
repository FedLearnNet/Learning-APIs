package bio.cosy.feddb.local.api.cohort.permission;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Objects;

@ApplicationScoped
public class PermissionServiceImpl implements PermissionService {

    @Inject
    PermissionBO bo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    @Override
    public List<PermissionDTO> list() {
        return bo.getAll();
    }

    @Override
    public PermissionDTO retrieve(Long id) {
        return bo.findById(id);
    }

    @Override
    @Transactional
    public Response create(PermissionDTO createDTO) {
        cohortMemberAuthBO.checkForEdit(createDTO.getCohortId(), userIdentity.getKeycloakId());
        PermissionDTO permission = bo.create(createDTO);
        return Response.status(Response.Status.CREATED).entity(permission).build();
    }

    @Override
    @Transactional
    public PermissionDTO update(Long id, PermissionDTO updateDTO) {
        PermissionDTO existing = bo.findById(id);
        String keycloakId = userIdentity.getKeycloakId();
        cohortMemberAuthBO.checkForEdit(existing.getCohortId(), keycloakId);
        if (!Objects.equals(existing.getCohortId(), updateDTO.getCohortId())) {
            cohortMemberAuthBO.checkForEdit(updateDTO.getCohortId(), keycloakId);
        }
        return bo.update(id, updateDTO);
    }

    @Override
    @Transactional
    public Response delete(Long id) {
        try {
            PermissionDTO existing = bo.findById(id);
            cohortMemberAuthBO.checkForDelete(existing.getCohortId(), userIdentity.getKeycloakId());
            bo.deleteById(id);
        } catch (NotFoundException ignored) {
            // Idempotent delete for missing permissions.
        }
        return Response.ok().build();
    }
}

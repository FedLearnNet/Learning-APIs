package bio.cosy.feddb.local.api.workflow;

import bio.cosy.feddb.core.api.workflow.WorkflowCreateDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
public class WorkflowServiceImpl implements WorkflowService {
    @Inject
    WorkflowBO bo;

    @Inject
    UserIdentity userIdentity;

    @Override
    public WorkflowDTO retrieve(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.get(id, keycloakId);
    }

    @Override
    @Transactional
    public Response create(WorkflowCreateDTO createDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        WorkflowDTO dto = bo.create(createDTO, keycloakId);
        return Response.status(Response.Status.CREATED).entity(dto).build();
    }

    @Override
    @Transactional
    public WorkflowDTO update(Long id, WorkflowDTO updateDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.update(updateDTO, keycloakId);
    }

    @Override
    @Transactional
    public Response delete(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.delete(id, keycloakId);
        return Response.status(Response.Status.OK).build();
    }
}

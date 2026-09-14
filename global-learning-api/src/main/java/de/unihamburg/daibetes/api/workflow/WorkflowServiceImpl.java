package de.unihamburg.daibetes.api.workflow;

import bio.cosy.feddb.core.api.workflow.WorkflowCreateDTO;
import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import de.unihamburg.daibetes.api.workflow.export.WorkflowExportBO;
import de.unihamburg.daibetes.dto.URLDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
public class WorkflowServiceImpl implements WorkflowService {
    @Inject
    WorkflowBO bo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    WorkflowExportBO workflowExportBO;

    @Override
    public List<WorkflowDTO> listWorkflows() {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.listWorkflows(keycloakId);
    }

    @Override
    public List<WorkflowDTO> listWorkflowForApp(Long appId) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.listWorkflowForApp(appId, keycloakId);
    }

    @Override
    public WorkflowDTO retrieve(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.get(id, keycloakId);
    }

    @Override
    @Transactional
    public WorkflowDTO create(WorkflowCreateDTO createDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.create(createDTO, keycloakId);
    }

    @Override
    @Transactional
    public WorkflowDTO createNextEmpty() {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.createNextEmpty(keycloakId);
    }

    @Override
    @Transactional
    public WorkflowDTO update(Long id, WorkflowDTO updateDTO) {
        String keycloakId = userIdentity.getKeycloakId();

        // Validate that path ID matches body ID
        if (updateDTO.getId() != null && !updateDTO.getId().equals(id)) {
            throw new BadRequestException("Path ID and body ID must match");
        }

        return bo.update(updateDTO, keycloakId);
    }

    @Override
    @Transactional
    public Response delete(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.delete(id, keycloakId);
        return Response.status(Response.Status.OK).build();
    }

    @Override
    public URLDTO saveWorkflowAsJson(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return workflowExportBO.export(id, keycloakId);
    }
}

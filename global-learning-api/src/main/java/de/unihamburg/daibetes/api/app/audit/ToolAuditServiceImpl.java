package de.unihamburg.daibetes.api.app.audit;

import bio.cosy.feddb.core.api.app.ToolAuditDTO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionBO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
public class ToolAuditServiceImpl implements ToolAuditService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    ToolAuditBO bo;

    @Inject
    FederatedAppVersionBO federatedAppVersionBO;

    @Override
    public List<ToolAuditDTO> list(Long toolId, String filterKeycloakId) {
        return bo.list(toolId, filterKeycloakId);
    }

    @Override
    public List<ToolAuditPendingDTO> listPending() {
        String keycloakId = userIdentity.getKeycloakId();
        keycloakId = "asfasdf";
        return federatedAppVersionBO.listForAudit(keycloakId);
    }

    @Override
    public ToolAuditDTO get(Long id) {
        return bo.getById(id);
    }

    @Override
    public ToolAuditCombinationDTO getByVersion(Long id) {
        String keycloakId = userIdentity.getKeycloakId();

        return bo.getByVersion(id,keycloakId);
    }

    @Override
    @Transactional
    public ToolAuditDTO create(ToolAuditDTO dto) {
        String keycloakId = userIdentity.getKeycloakId();

        return bo.create(dto, keycloakId);
    }

    @Override
    @Transactional
    public ToolAuditDTO update(Long id, ToolAuditDTO dto) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.update(id, dto, keycloakId);
    }

    @Override
    @Transactional
    public Response delete(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.delete(id, keycloakId);
        return Response.ok().build();
    }
}

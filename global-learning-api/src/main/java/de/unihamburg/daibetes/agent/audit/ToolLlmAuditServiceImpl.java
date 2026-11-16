package de.unihamburg.daibetes.agent.audit;

import bio.cosy.feddb.core.security.Roles;
import de.unihamburg.daibetes.agent.audit.pojo.ToolLlmAuditCaller;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class ToolLlmAuditServiceImpl implements ToolLlmAuditService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    ToolLlmAuditBO bo;

    @Inject
    ToolLlmAuditLimitBO limitBO;

    @Override
    public ToolLlmAuditDTO request(Long versionId, boolean force) {
        return bo.request(versionId, caller(), force);
    }

    @Override
    public List<ToolLlmAuditDTO> listByVersion(Long versionId) {
        return bo.listByVersionTransactional(versionId, caller());
    }

    @Override
    public Response findLatestByVersion(Long versionId) {
        return bo.findLatestByVersionTransactional(versionId, caller())
                .map(dto -> Response.ok(dto).build())
                .orElseGet(() -> Response.noContent().build());
    }

    @Override
    public ToolLlmAuditDTO get(Long id) {
        return bo.findByIdTransactional(id, caller());
    }

    @Override
    public ToolLlmAuditQuotaDTO quota() {
        ToolLlmAuditCaller caller = caller();
        return limitBO.quotaOf(caller.getKeycloakId(), caller.isAdmin());
    }

    private ToolLlmAuditCaller caller() {
        Set<String> roles = userIdentity.getRoles();
        return new ToolLlmAuditCaller(userIdentity.getKeycloakId(), roles.contains(Roles.ADMIN),
                roles.contains(Roles.GLOBAL_AUDITOR));
    }
}

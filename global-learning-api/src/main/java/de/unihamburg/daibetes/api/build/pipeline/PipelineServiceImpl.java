package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.pipeline.PipelineStatusUpdateDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineRunInfoDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineCallbackService;
import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import io.quarkus.security.Authenticated;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
@Authenticated
public class PipelineServiceImpl implements PipelineService {

    @Inject
    PipelineBO bo;

    @Inject
    UserIdentity userIdentity;


    @Override
    @Transactional
    public PipelineDTO create(PipelineCreateDTO request) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.create(request, keycloakId);
    }

    @Override
    @Transactional
    public PipelineDTO start(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.start(id, keycloakId);
    }

    @Override
    public List<PipelineDTO> list(Long appVersionId, Long appId, Long modelSubId) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.getAll(keycloakId, appVersionId, appId, modelSubId);
    }

    @Override
    public PipelineDTO get(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.getById(id, keycloakId);
    }

    @Override
    public PipelineDTO stopPipeline(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.stopPipeline(id, keycloakId);
    }

    @Override
    @Transactional
    public void processUpdate(Long id, String secret, PipelineStatusUpdateDTO dto) {
        bo.processStatusUpdate(id, secret, dto);
    }

    @Override
    @Transactional
    public void processSummary(Long id, String secret, AppPublishInfoDTO dto) {
        bo.processSummary(id, secret, dto);
    }

    @Override
    public Multi<PipelineDTO> stream(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.stream(id, keycloakId);
    }

    @Override
    @Transactional
    public PipelineRunInfoDTO getPipelineRunInfo(Long id, String secret) {
        return bo.getPipelineRunInfo(id, secret);
    }

    @Override
    @Transactional
    public Response getFiles(Long id, String secret) {
        return PipelineCallbackService.zip(id, bo.getFiles(id, secret));
    }
}

package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
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
    public PipelineDTO processUpdate(Long id, String secret, PipelineStatusUpdateDTO dto) {
        return bo.processStatusUpdate(id, secret, dto);
    }

    @Override
    @Transactional
    public PipelineDTO processSummary(Long id, String secret, AppPublishInfoDTO dto) {
        return bo.processSummary(id, secret, dto);
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
    public Response getFileMetadata(Long id, String secret) {
        byte[] zipBytes = bo.getFilesForPipeline(id, secret);

        String filename = "pipeline-" + id + "-files.zip";
        return Response.ok(zipBytes, "application/zip")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_LENGTH, zipBytes.length)
                .build();
    }
}

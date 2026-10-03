package bio.cosy.feddb.local.api.pipeline;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineCallbackService;
import bio.cosy.feddb.core.api.pipeline.PipelineRunInfoDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineStatusUpdateDTO;
import jakarta.annotation.security.PermitAll;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

@ApplicationScoped
@PermitAll
public class PipelineServiceImpl implements PipelineService {

    @Inject
    PipelineBO bo;

    @Override
    public void processUpdate(Long id, String secret, PipelineStatusUpdateDTO dto) {
        bo.processStatusUpdate(id, secret, dto);
    }

    @Override
    public void processSummary(Long id, String secret, AppPublishInfoDTO dto) {
        bo.processSummary(id, secret, dto);
    }

    @Override
    public PipelineRunInfoDTO getPipelineRunInfo(Long id, String secret) {
        return bo.getPipelineRunInfo(id, secret);
    }

    @Override
    public Response getFiles(Long id, String secret) {
        return PipelineCallbackService.zip(id, bo.getFiles(id, secret));
    }
}

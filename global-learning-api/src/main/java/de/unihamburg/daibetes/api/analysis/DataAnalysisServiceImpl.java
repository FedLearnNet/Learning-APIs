package de.unihamburg.daibetes.api.analysis;

import bio.cosy.feddb.core.api.file.FileRenameDTO;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisDetailDTO;
import bio.cosy.feddb.core.api.model.workflow.DataAnalysisFileDTO;
import bio.cosy.feddb.core.api.model.workflow.ModelWorkflowDTO;
import bio.cosy.feddb.core.api.run.AppRunTypeEnum;
import de.unihamburg.daibetes.api.analysis.file.DataAnalysisFileBO;
import de.unihamburg.daibetes.api.analysis.report.DataAnalysisReportRendererBO;
import de.unihamburg.daibetes.api.analysis.worklfow.DataAnalysisWorkflowRunBO;
import de.unihamburg.daibetes.api.analysis.worklfow.DataAnalysisWorkflowRunDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.ByteArrayOutputStream;
import java.util.List;

@ApplicationScoped
public class DataAnalysisServiceImpl implements DataAnalysisService {
    @Inject
    DataAnalysisBO bo;

    @Inject
    UserIdentity userIdentity;

    @Inject
    DataAnalysisFileBO modelWorkflowFileBO;

    @Inject
    DataAnalysisWorkflowRunBO workflowRunBO;

    @Inject
    DataAnalysisReportRendererBO reportRendererBO;

    @Override
    public List<ModelWorkflowDTO> list() {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.list(keycloakId);
    }

    @Override
    public DataAnalysisDetailDTO retrieve(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.findById(id, keycloakId);
    }

    @Override
    public DataAnalysisWorkflowRunDTO retrieveWorkflowExperiment(Long id, Long experimentId) {
        String keycloakId = userIdentity.getKeycloakId();
        return workflowRunBO.findById(id, experimentId, keycloakId);
    }

    @Override
    @Transactional
    public Response delete(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.delete(id, keycloakId);
        return Response.ok().build();
    }

    @Override
    @Transactional
    public Response createWorkflow(ModelWorkflowDTO workflow) {
        String keycloakId = userIdentity.getKeycloakId();
        String name = workflow.getName();
        return Response.status(Response.Status.CREATED).entity(bo.createWorkflow(keycloakId, name)).build();
    }

    @Override
    public List<DataAnalysisFileDTO> listFiles(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelWorkflowFileBO.listAll(id, keycloakId, false);
    }

    @Override
    @Transactional
    public Response upload(Long id, FileUpload file) {
        String keycloakId = userIdentity.getKeycloakId();
        return Response.status(Response.Status.CREATED)
                .entity(modelWorkflowFileBO.storeFile(id, file, keycloakId))
                .build();
    }

    @Override
    @Transactional
    public DataAnalysisFileDTO linkFile(Long id, Long fileId) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelWorkflowFileBO.linkFile(id, fileId, keycloakId);
    }

    @Override
    @Transactional
    public Response deleteFile(Long workflowId, Long fileId) {
        String keycloakId = userIdentity.getKeycloakId();

        modelWorkflowFileBO.delete(fileId, workflowId, keycloakId);
        return Response.ok().build();
    }

    @Override
    @Transactional
    public DataAnalysisFileDTO renameFile(Long id, Long fileId, FileRenameDTO fileRenameDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        return modelWorkflowFileBO.renameFile(id, fileId, fileRenameDTO, keycloakId);
    }

    @Override
    @Transactional
    public Response getReport(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        byte[] pdf = reportRendererBO.render(id, keycloakId);
        return Response.ok(pdf)
                .header("Content-Disposition", "inline; filename=\"data-analysis-" + id + "-report.pdf\"")
                .build();
    }
}

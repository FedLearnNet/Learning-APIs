package de.unihamburg.daibetes.api.testembed;

import bio.cosy.feddb.core.api.model.ModelDTO;
import bio.cosy.feddb.core.api.run.AppMessageWrapperDTO;
import bio.cosy.feddb.core.api.run.AppRunTypeEnum;
import bio.cosy.feddb.core.api.run.AppRunUploadData;
import bio.cosy.feddb.core.api.run.OutputRunDataDTO;
import bio.cosy.feddb.core.helper.FileHelper;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

@ApplicationScoped
public class TestEmbedUploadServiceImpl implements TestEmbedUploadService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    TestEmbedBO testEmbedBO;

    @Override
    @Transactional
    public AppMessageWrapperDTO<ModelDTO> uploadModel(Long appId, ModelSubDataDTO modelData) {
        String keycloakId = userIdentity.getKeycloakId();
        return testEmbedBO.saveModel(modelData, keycloakId);
    }

    @Override
    @Transactional
    public Response uploadOutput(Long appId, AppRunTypeEnum runType, AppRunUploadData req) {
        String keycloakId = userIdentity.getKeycloakId();
        OutputRunDataDTO data = new OutputRunDataDTO();
        data.setRunId(req.getRunId());
        data.setOutputData(req.getFieldsNullsafe());
        req.getFilesNullsafe().forEach(fileUpload -> {
            try {
                String key = AppRunUploadData.getKey(fileUpload);
                String base64Encoded = FileHelper.formToBase64(fileUpload);
                data.getOutputData().put(key, base64Encoded);
            } catch (IOException e) {
                throw new RuntimeException("Error reading bytes from InputStream", e);
            }
        });
        AppMessageWrapperDTO<?> finishedTest = testEmbedBO.uploadData(appId, data, runType, keycloakId);
        return Response.status(Response.Status.CREATED).entity(finishedTest).build();
    }


    @Override
    @Transactional
    public Response downloadOutput(Long id, Long runId, AppRunTypeEnum runType) {
        String keycloakId = userIdentity.getKeycloakId();
        ByteArrayOutputStream byteArrayOutputStream = testEmbedBO.downloadOutput(id, runId, runType, keycloakId);
        return Response.ok(byteArrayOutputStream.toByteArray())
                .header("Content-Disposition", "attachment; filename=download.zip")
                .header("Content-Type", "application/zip")
                .build();
    }
}

package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.api.run.AppRunUploadData;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.learning.project.run.data.FederatedLearningExperimentStepDataBO;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.reactive.messaging.Channel;

import java.io.ByteArrayOutputStream;

@ApplicationScoped
public class FederatedLearningExperimentServiceImpl implements FederatedLearningExperimentService {
    public static final String EXPERIMENT_CHANNEL = "experiment-info";

    @Inject
    UserIdentity userIdentity;

    @Inject
    FederatedLearningExperimentStepDataBO federatedLearningExperimentStepDataBO;

    @Inject
    @Channel(EXPERIMENT_CHANNEL)
    Multi<FederatedLearningExperimentDTO> fedInfo;

    @Override
    @Transactional
    public Response uploadOutput(Long runId, AppRunUploadData req) {
        federatedLearningExperimentStepDataBO.saveUploadedOutput(runId, req.getFilesNullsafe());
        return Response.status(Response.Status.CREATED).entity(null).build();
    }

    @Override
    @Transactional
    public Response uploadModel(Long appId, ModelSubDataDTO file) {
        federatedLearningExperimentStepDataBO.saveResults(appId, file);
        return Response.status(Response.Status.CREATED).entity(null).build();
    }

    @Override
    @Transactional
    public Response downloadOutput(String containerId) {
        String keycloakId = userIdentity.getKeycloakId();
        ByteArrayOutputStream byteArrayOutputStream = null; //testEmbedBO.downloadOutput(id, runId, runType, keycloakId);
        return Response.ok(byteArrayOutputStream.toByteArray()).header("Content-Disposition", "attachment; filename=download.zip").header("Content-Type", "application/zip").build();
    }
}

package bio.cosy.feddb.local.api.pipeline;

import bio.cosy.feddb.core.api.pipeline.BasePipelineBO;
import bio.cosy.feddb.core.api.pipeline.PipelineRunInfoDTO;
import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import bio.cosy.feddb.core.api.pipeline.PipelineStatusUpdateDTO;
import bio.cosy.feddb.core.api.pipeline.StepName;
import bio.cosy.feddb.core.helper.FileHelper;
import bio.cosy.feddb.core.services.orch.dto.StartPipelineDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerAO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerBO;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerEntity;
import bio.cosy.feddb.local.api.importer.transformer.code.CodeTransformerStatus;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import bio.cosy.feddb.local.services.orch.OrchContainerServiceClient;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Local image builds: the Tool-Build-Pipeline runs without clone and push, gets its files as zip and reports
 * every step back through the {@link PipelineService} callbacks.
 */
@ApplicationScoped
public class PipelineBO extends BasePipelineBO<PipelineDTO, PipelineEntity, PipelineAO, PipelineMapper> {

    static final List<StepName> STEPS = List.of(StepName.FETCH_CONFIG, StepName.FETCH_FILES, StepName.BUILD_IMAGE,
            StepName.SCAN_IMAGE, StepName.MALWARE_CHECK, StepName.RUN_PYTEST, StepName.COLLECT_SUMMARY);
    private static final Duration CLEANUP_DELAY = Duration.ofSeconds(10);

    @Inject
    CodeTransformerAO codeTransformerAO;

    @Inject
    CodeTransformerBO codeTransformerBO;

    @Inject
    @RestClient
    OrchContainerServiceClient containerClient;

    @Inject
    FLNetClientConfig config;

    @ConfigProperty(name = "quarkus.http.port")
    int serverPort;

    public void start(Long codeTransformerId) {
        PipelineEntity pipeline = QuarkusTransaction.requiringNew().call(() -> create(codeTransformerId));
        StartPipelineDTO start = new StartPipelineDTO();
        start.setPipelineId(pipeline.getId());
        start.setAppImage(config.connector().codeTransformerPipelineImage());
        start.setLocalOnly(true);
        start.addEnvironment(pipeline.getId(), pipeline.getSecret());
        try {
            String containerId = containerClient.startPipeline(start, true, "", serverPort).getId();
            QuarkusTransaction.requiringNew().run(() -> ao.findById(pipeline.getId()).setContainerId(containerId));
        } catch (RuntimeException e) {
            PipelineStatusUpdateDTO failed = new PipelineStatusUpdateDTO();
            failed.setStepName(StepName.FETCH_CONFIG);
            failed.setStatus(PipelineStatus.FAILED);
            failed.setErrorCode(e.getClass().getSimpleName());
            failed.setErrorMessage("The build pipeline could not be started: " + e.getMessage());
            QuarkusTransaction.requiringNew().run(() -> apply(ao.findById(pipeline.getId()), failed));
        }
    }

    public Multi<PipelineDTO> updatesOf(Long codeTransformerId) {
        return updates.select().where(dto -> codeTransformerId.equals(dto.getCodeTransformerId())).onOverflow().drop();
    }

    @Override
    protected PipelineRunInfoDTO runInfo(PipelineEntity pipeline) {
        PipelineRunInfoDTO info = new PipelineRunInfoDTO();
        info.setImageName(CodeTransformerBO.repository(pipeline.getCodeTransformer()));
        info.setDockerTag(pipeline.getDockerTag());
        return info;
    }

    @Override
    protected byte[] files(PipelineEntity pipeline) {
        return FileHelper.textsToZip(pipeline.getCodeTransformer().getFiles());
    }

    @Override
    protected void finished(PipelineEntity pipeline) {
        codeTransformerBO.finished(pipeline);
        cleanup(pipeline.getContainerId());
    }

    private PipelineEntity create(Long codeTransformerId) {
        CodeTransformerEntity codeTransformer = codeTransformerAO.findById(codeTransformerId);
        codeTransformer.setStatus(CodeTransformerStatus.BUILDING);
        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setCodeTransformer(codeTransformer);
        pipeline.setPipelineStatus(PipelineStatus.PENDING);
        pipeline.setSecret(RandomStringUtils.secureStrong().nextAlphanumeric(32));
        pipeline.setDockerTag(codeTransformer.getCodeHash());
        STEPS.forEach(name -> {
            PipelineStepEntity step = new PipelineStepEntity();
            step.setName(name);
            step.setStepStatus(PipelineStatus.PENDING);
            step.setPipeline(pipeline);
            pipeline.getSteps().add(step);
        });
        ao.persist(pipeline);
        updates.onNext(mapper.entityToDto(pipeline));
        return pipeline;
    }

    /**
     * Lets orch remove the finished pipeline container and its dedicated network once it had time to exit.
     */
    private void cleanup(String containerId) {
        if (containerId == null) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                containerClient.cleanupWorkflow(containerId, true);
            } catch (RuntimeException e) {
                Log.warnf("Could not clean up the pipeline container %s: %s", containerId, e.getMessage());
            }
        }, CompletableFuture.delayedExecutor(CLEANUP_DELAY.toSeconds(), TimeUnit.SECONDS));
    }
}

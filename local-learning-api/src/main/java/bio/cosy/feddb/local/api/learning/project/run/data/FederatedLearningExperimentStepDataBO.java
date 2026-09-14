
package bio.cosy.feddb.local.api.learning.project.run.data;

import bio.cosy.feddb.core.api.app.FederatedAppType;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import bio.cosy.feddb.core.api.run.AppRunUploadData;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.file.FileAO;
import bio.cosy.feddb.local.api.file.FileBO;
import bio.cosy.feddb.local.api.file.FileEntity;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepAO;
import bio.cosy.feddb.local.api.learning.project.run.step.FederatedLearningExperimentStepEntity;
import bio.cosy.feddb.local.api.workflow.connection.WorkflowConnectionAO;
import bio.cosy.feddb.local.api.workflow.connection.WorkflowConnectionBO;
import bio.cosy.feddb.local.api.workflow.connection.WorkflowConnectionEntity;
import bio.cosy.feddb.local.services.GlobalAPIModelResultService;
import bio.cosy.feddb.local.services.orch.WorkflowOrchestratorBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class FederatedLearningExperimentStepDataBO extends BaseBo<FederatedLearningExperimentStepDataDTO, FederatedLearningExperimentStepDataEntity, FederatedLearningExperimentStepDataAO, FederatedLearningExperimentStepDataMapper> {

    @Inject
    FileBO fileBO;

    @Inject
    FileAO fileAO;

    @Inject
    WorkflowOrchestratorBO workflowOrchestratorBO;

    @Inject
    @RestClient
    GlobalAPIModelResultService globalAPIModelResultService;


    @Inject
    FederatedLearningExperimentStepAO stepAO;

    @Inject
    WorkflowConnectionAO workflowConnectionAO;

    @Inject
    WorkflowConnectionBO workflowConnectionBO;

    public List<FederatedLearningExperimentStepDataDTO> findByStep(Long id) {
        return mapper.entitiesToDtos(ao.findByStep(id));
    }

    /*
     * Creates files and associates them with the given step in a new transaction.
     * Create them as output and inputs directly, so the output can be used as input in the next step.
     */
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public List<FileDTO> createForStep(List<File> files, Long workflowNodeId, Long experimentId, Long stepId) {
        if (files == null || files.isEmpty() || workflowNodeId == null) {
            return new ArrayList<>();
        }
        FederatedLearningExperimentStepEntity step = stepAO.findById(stepId);
        if (step == null) {
            Log.errorf("Could not find step with id %s while persisting workflow results", stepId);
            return new ArrayList<>();
        }
        List<FileDTO> internalFiles = fileBO.create(files);
        List<WorkflowConnectionEntity> edges = workflowConnectionAO.findByWorkflowOutputNodeId(workflowNodeId);

        for (FileDTO file : internalFiles) {
            this.create(file, workflowNodeId, experimentId, step, edges);
        }
        return internalFiles;
    }

    /**
     * Persists output files pushed by the app's client (POST /learning/run/{runId}/upload/output when
     * ENABLE_REMOTE_RESULT_SAVING is on). Each multipart part keeps its real name in fileName(); we
     * copy the uploaded temp file under that name so workflow-connection edge matching and display work,
     * then reuse createForStep to store them as this step's output.
     */
    public void saveUploadedOutput(Long stepId, List<FileUpload> uploads) {
        if (uploads == null || uploads.isEmpty()) {
            Log.infof("No uploaded output files for step %s", stepId);
            return;
        }
        FederatedLearningExperimentStepEntity step = stepAO.findById(stepId);
        if (step == null) {
            Log.errorf("Could not find step with id %s while persisting uploaded output", stepId);
            return;
        }
        List<File> files = new ArrayList<>();
        try {
            java.nio.file.Path tmpDir = java.nio.file.Files.createTempDirectory("fl-output-" + stepId + "-");
            for (FileUpload upload : uploads) {
                String name = upload.fileName() != null ? upload.fileName() : AppRunUploadData.getName(upload);
                if (name == null || name.isBlank()) {
                    name = upload.name();
                }
                java.nio.file.Path dest = tmpDir.resolve(name);
                java.nio.file.Files.copy(upload.uploadedFile(), dest,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                files.add(dest.toFile());
            }
        } catch (Exception e) {
            Log.errorf(e, "Failed to stage uploaded output files for step %s", stepId);
            return;
        }
        createForStep(files, step.getWorkflowNode().getId(), step.getExperiment().getId(), stepId);
        Log.infof("Persisted %d uploaded output file(s) for step %s", files.size(), stepId);
    }

    public void saveResults(Long stepId) {
        FederatedLearningExperimentStepEntity step = stepAO.findById(stepId);
        if (step == null) {
            Log.errorf("Could not find step with id %s", stepId);
            throw new IllegalStateException("Cannot collect results: step " + stepId + " not found");
        }
        StepResultContext context = new StepResultContext(
                stepId,
                step.getWorkflowNode().getId(),
                step.getWorkflowNode().getExecutionOrder(),
                step.getExperiment().getId(),
                step.getWorkflowNode().getFederatedAppVersionId(),
                step.getExperiment().getUniqueRandomClinicId(),
                step.getExperiment().getProject().getRequest().getGlobalFLExperimentUniqueId(),
                Boolean.TRUE.equals(step.getExperiment().getProject().getIsCoordinator()),
                Boolean.TRUE.equals(step.getExperiment().getProject().getRequest().getModelCanBePublic()),
                null
        );
        saveResults(context);
    }


    public void saveResults(Long stepId, ModelSubDataDTO file) {
        FederatedLearningExperimentStepEntity step = stepAO.findById(stepId);
        if (step == null) {
            Log.errorf("Could not find step with id %s", stepId);
            return;
        }
        Boolean isCoordinator = step.getExperiment().getProject().getIsCoordinator();
        if (isCoordinator == null) {
            isCoordinator = false;
        }
        if (!isCoordinator) {
            Log.infof("Skipping upload of model result to global API, not a coordinator");
            return;
        }
        StepResultContext context = new StepResultContext(
                stepId,
                step.getWorkflowNode().getId(),
                step.getWorkflowNode().getExecutionOrder(),
                step.getExperiment().getId(),
                step.getWorkflowNode().getFederatedAppVersionId(),
                step.getExperiment().getUniqueRandomClinicId(),
                step.getExperiment().getProject().getRequest().getGlobalFLExperimentUniqueId(),
                isCoordinator,
                Boolean.TRUE.equals(step.getExperiment().getProject().getRequest().getModelCanBePublic()),
                null
        );
        uploadFiles(file.getFile(), context);
    }

    public void saveResults(StepResultContext context) {
        if (context.executionOrder() == null || context.executionOrder() < 0) {
            throw new IllegalStateException("Cannot collect results for step " + context.stepId()
                    + ": missing or invalid workflow node execution order");
        }
        Log.infof("Collecting results: experimentId=%d stepId=%d workflowNodeId=%d executionOrder=%d",
                context.experimentId(), context.stepId(), context.workflowNodeId(), context.executionOrder());
        List<File> files = workflowOrchestratorBO.getFiles(context.experimentId(), context.executionOrder().longValue());
        Log.infof("Downloaded %d output file(s): experimentId=%d stepId=%d executionOrder=%d",
                files.size(), context.experimentId(), context.stepId(), context.executionOrder());
        if (files.isEmpty()) {
            Log.warnf("No output files found: experimentId=%d stepId=%d executionOrder=%d; no files will be persisted",
                    context.experimentId(), context.stepId(), context.executionOrder());
        }
        List<FileDTO> fileDTOs = createForStep(files, context.workflowNodeId(), context.experimentId(), context.stepId());
        Log.infof("Persisted %d output file(s): experimentId=%d stepId=%d", fileDTOs.size(), context.experimentId(), context.stepId());
        if (!FederatedAppType.ANALYSIS.equals(context.appType())) {
            Log.infof("Skipping upload of model result to global API, not an analysis app");
            return;
        }
        uploadFiles(files, context, fileDTOs);
    }


    public FederatedLearningExperimentStepDataDTO create(FileDTO file,
                                                         Long workflowNodeId,
                                                         Long experimentId,
                                                         FederatedLearningExperimentStepEntity step,
                                                         List<WorkflowConnectionEntity> edges) {
        if (file == null || workflowNodeId == null) {
            return null;
        }
        String fileName = file.getFileName();
        WorkflowConnectionEntity edge = workflowConnectionBO.filterForFileName(edges, fileName);
        if (edge == null) {
            Log.warnf("No workflow connection found for workflow node id %d and fileName %s", workflowNodeId, fileName);
        }
        FederatedLearningExperimentStepDataEntity entity = new FederatedLearningExperimentStepDataEntity();
        entity.setFile(fileAO.findById(file.getId()));
        entity.setStepOutput(step);
        if (edge != null) {
            Long inputNodeId = edge.getInputNode().getId();
            stepAO.findByWorkflowNodeId(experimentId, inputNodeId)
                    .ifPresent(entity::setStepInput);
        }
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void createForStep(File file, Long stepId) {
        FederatedLearningExperimentStepEntity step = stepAO.findById(stepId);
        if (step == null) {
            Log.errorf("Could not find step with id %s while persisting input data", stepId);
            return;
        }
        FileEntity fileEntity = fileBO.createEntityForSystem(file);

        FederatedLearningExperimentStepDataEntity entity = new FederatedLearningExperimentStepDataEntity();
        entity.setFile(fileEntity);
        entity.setStepInput(step);
        ao.persist(entity);
        mapper.entityToDto(entity);
    }

    public void uploadFiles(FileUpload file, StepResultContext context) {
        if (!context.coordinator()) {
            Log.infof("Skipping upload of model result to global API, not a coordinator");
            return;
        }
        if (!context.modelCanBePublic()) {
            Log.infof("Skipping upload of model result to global API, model cannot be public");
            return;
        }
        ClientMultipartForm form = buildClientMultipartForm(
                file,
                context.globalFLExperimentUniqueId(),
                context.appVersionId(),
                context.clinicId()
        );
        Log.infof("Uploading model result to global API");
        try (Response r = globalAPIModelResultService.upload(form)) {
            if (r.getStatus() == Response.Status.CREATED.getStatusCode()) {
                Log.info("Successfully uploaded model result to global API");
            } else if (r.getStatus() == Response.Status.BAD_REQUEST.getStatusCode()) {
                Log.errorf("Failed to upload model result to global API due to bad request, status: %d, reason: %s", r.getStatus(), r.readEntity(String.class));
            } else {
                Log.errorf("Failed to upload model result to global API, status: %d", r.getStatus());
            }
        } catch (Exception e) {
            Log.error("Failed to upload model result to global API", e);
        }

    }

    public void uploadFiles(List<File> files, StepResultContext context, List<FileDTO> internalFiles) {
        if (!context.coordinator()) {
            Log.infof("Skipping upload of model result to global API, not a coordinator");
            return;
        }
        if (!context.modelCanBePublic()) {
            Log.infof("Skipping upload of model result to global API, model cannot be public");
            return;
        }
        ClientMultipartForm form = buildClientMultipartForm(
                files,
                internalFiles,
                context.globalFLExperimentUniqueId(),
                context.appVersionId(),
                context.clinicId()
        );
        Log.infof("Uploading model result to global API");
        try (Response r = globalAPIModelResultService.upload(form)) {
            if (r.getStatus() == Response.Status.CREATED.getStatusCode()) {
                Log.info("Successfully uploaded model result to global API");
            } else if (r.getStatus() == Response.Status.BAD_REQUEST.getStatusCode()) {
                Log.errorf("Failed to upload model result to global API due to bad request, status: %d, reason: %s", r.getStatus(), r.readEntity(String.class));
            } else {
                Log.errorf("Failed to upload model result to global API, status: %d", r.getStatus());
            }
        } catch (Exception e) {
            Log.error("Failed to upload model result to global API", e);
        }

    }

    public ClientMultipartForm buildClientMultipartForm(
            List<File> files,
            List<FileDTO> internalFiles,
            String globalFLExperimentUniqueId,
            Long appVersionId,
            String clinicId
    ) {
        ClientMultipartForm form = ClientMultipartForm.create();

        form.attribute("globalFLExperimentUniqueId", String.valueOf(globalFLExperimentUniqueId), "experimentId");
        form.attribute("appVersionId", String.valueOf(appVersionId), "appVersionId");
        form.attribute("clinicId", clinicId, "clinicId");

        String contentType = "application/octet-stream";
        for (File f : files) {
            FileDTO d = internalFiles.stream()
                    .filter(fileDTO -> fileDTO.getFileName().equals(f.getName()))
                    .findFirst()
                    .orElse(null);
            if (d != null) {
                contentType = d.getContentType();
            }
            Path p = f.toPath();
            form.binaryFileUpload("files", f.getName(), p.toString(), contentType);
        }

        return form;
    }

    public ClientMultipartForm buildClientMultipartForm(
            FileUpload file,
            String globalFLExperimentUniqueId,
            Long appVersionId,
            String clinicId
    ) {
        ClientMultipartForm form = ClientMultipartForm.create();

        form.attribute("globalFLExperimentUniqueId", String.valueOf(globalFLExperimentUniqueId), "experimentId");
        form.attribute("appVersionId", String.valueOf(appVersionId), "appVersionId");
        form.attribute("clinicId", clinicId, "clinicId");

        String contentType = "application/octet-stream";

        Path p = file.uploadedFile();
        form.binaryFileUpload("files", file.fileName(), p.toString(), contentType);
        return form;
    }
}

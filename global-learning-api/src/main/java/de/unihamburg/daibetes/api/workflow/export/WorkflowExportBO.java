package de.unihamburg.daibetes.api.workflow.export;

import bio.cosy.feddb.core.api.app.PublishStatus;
import de.unihamburg.daibetes.api.app.ToolExternalHandler;
import de.unihamburg.daibetes.api.workflow.WorkflowAO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.connection.WorkflowConnectionBO;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeBO;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.dto.URLDTO;
import de.unihamburg.daibetes.helper.PathOrUrl;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkflowExportBO {

    @Inject
    WorkflowAO workflowAO;

    @Inject
    WorkflowExportMapper workflowExportMapper;

    @Inject
    ToolExternalHandler toolExternalHandler;

    @Inject
    FLNetConfig flnet;

    @Inject
    WorkflowNodeBO workflowNodeBO;

    @Inject
    WorkflowConnectionBO workflowConnectionBO;

    public URLDTO export(Long workflowId, String keycloakId) {
        Optional<WorkflowEntity> entityOpt = workflowAO.findByUserIdAndWorkflowId(workflowId, keycloakId);
        if (entityOpt.isEmpty()) {
            throw new NotFoundException("Workflow not found");
        }
        PublishStatus status = entityOpt.get().getPublishStatus();
        if (status == null || !status.equals(PublishStatus.PUBLISHED)) {
            throw new NotFoundException("Cannot export unpublished workflow");
        }
        WorkflowExportDTO exportDTO = workflowExportMapper.entityToDto(entityOpt.get());
        String fileName = nameToFileName(exportDTO.getName());
        Optional<Path> path = toolExternalHandler.saveWorkflowAsJson(exportDTO, fileName);
        if (path.isEmpty()) {
            throw new NotFoundException("Failed to save workflow as json");
        }
        return new URLDTO(path.get().toUri().toString());
    }

    public void importWorkflows() {
        List<WorkflowExportDTO> toImport = readWorkflows();
        if (toImport.isEmpty()) {
            Log.infof("No workflows to import");
            return;
        }
        for (WorkflowExportDTO export : toImport) {
            try {
                importWorkflowTransactional(export);
            } catch (Exception e) {
                Log.errorf(e, "Failed to import workflow %s", export.getName());
            }
        }
    }

    @Transactional
    public void importWorkflowTransactional(WorkflowExportDTO workflow) {
        WorkflowEntity entity = workflowExportMapper.dtoToEntity(workflow);
        workflowAO.persist(entity);
        Set<WorkflowNodeEntity> nodes = workflow.getNodes()
                .stream()
                .map(n -> workflowNodeBO.createEntity(entity, n))
                .collect(Collectors.toSet());

        workflow.getConnections()
                .stream()
                .map(n -> workflowConnectionBO.createEntity(entity, n, nodes))
                .collect(Collectors.toSet());


    }


    public List<WorkflowExportDTO> readWorkflows() {
        if (!flnet.workflowImports().enabled()) {
            return List.of();
        }

        if (flnet.workflowImports().paths().isEmpty()) {
            Log.warnf("Workflow import enabled but no paths configured");
            return List.of();
        }

        List<Path> roots = flnet.workflowImports().paths().get();
        if (roots.isEmpty()) {
            Log.warnf("Workflow import enabled but no paths configured");
            return List.of();
        }

        List<PathOrUrl> jsonSources = roots.stream()
                .flatMap(toolExternalHandler::resolveJsonSources)
                .sorted(Comparator.comparing(PathOrUrl::sortKey))
                .toList();

        return toolExternalHandler.loadAllWorkflows(jsonSources);
    }

    private String nameToFileName(String name) {
        String prefix = "workflow_export_";
        String suffix = ".json";
        String cleanName = name.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
        String randomHash = Integer.toHexString(ThreadLocalRandom.current().nextInt());
        return prefix + cleanName + "_" + randomHash + suffix;
    }
}

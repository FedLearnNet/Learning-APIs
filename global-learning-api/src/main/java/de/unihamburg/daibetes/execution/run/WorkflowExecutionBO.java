package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.api.workflow.base.BaseWorkflowEngine;
import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.executor.BaseWorkflowExecutionBO;
import bio.cosy.feddb.core.execution.executor.ExecutionStartDTO;
import de.unihamburg.daibetes.api.workflow.WorkflowAO;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowMapper;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeAO;
import de.unihamburg.daibetes.api.workflow.node.WorkflowNodeEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.LinkedHashMap;
import java.util.List;

@ApplicationScoped
public class WorkflowExecutionBO extends BaseWorkflowExecutionBO<ExecutionRunEntity, ExecutionRunStepEntity,
        WorkflowNodeEntity, ExecutionRunAO> {

    private static final BaseWorkflowEngine WORKFLOW_ENGINE = new BaseWorkflowEngine();

    @Inject
    WorkflowAO workflowAO;

    @Inject
    WorkflowMapper workflowMapper;

    public WorkflowExecutionBO() {
        super(ExecutionRunEntity::new, ExecutionRunStepEntity::new, ExecutionLocation.PLATFORM);
    }

    @Transactional
    public ExecutionRunEntity startWorkflowTransactional(ExecutionStartDTO start, Long workflowId, LinkedHashMap<String, Object> inputs,
                                            String keycloakId) {

        WorkflowEntity workflow = workflowAO.findByUserIdAndWorkflowId(workflowId, keycloakId)
                .orElseThrow(() -> new NotFoundException("Workflow " + workflowId + " not found"));
        start.setName(workflow.getName());
        WORKFLOW_ENGINE.getInputNodes(workflowMapper.entityToDto(workflow)).forEach((input, connections) -> {
            Object value = inputs.get(input.getName());
            if (value != null) {
                connections.forEach((connection, node) -> start.getInputs()
                        .computeIfAbsent(node.getId(), id -> new LinkedHashMap<>())
                        .put(connection.getInputFileName(), value));
            }
        });
        return executeTransactional(start, workflow.getNodes());
    }
}

package bio.cosy.feddb.local.execution.run;

import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.executor.BaseWorkflowExecutionBO;
import bio.cosy.feddb.core.execution.executor.ExecutionStartDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.local.api.workflow.node.WorkflowNodeEntity;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.LinkedHashMap;
import java.util.function.Consumer;

@ApplicationScoped
public class WorkflowExecutionBO extends BaseWorkflowExecutionBO<ExecutionRunEntity, ExecutionRunStepEntity,
        WorkflowNodeEntity, ExecutionRunAO> {

    public WorkflowExecutionBO() {
        super(ExecutionRunEntity::new, ExecutionRunStepEntity::new, ExecutionLocation.LOCAL);
    }

    @Transactional
    public ExecutionRunEntity executeApp(String name, LinkedHashMap<String, Object> hyperParams, Consumer<ExecutionRunStepEntity> app) {
        ExecutionStartDTO start = new ExecutionStartDTO();
        start.setPurpose(ExecutionPurpose.PROCESSING);
        start.setName(name);
        HyperParamSearchDTO search = HyperParamSearchDTO.fixed(hyperParams != null ? hyperParams : new LinkedHashMap<>());
        return executeSingle(start, search, null, step -> {
            step.setAppName(name);
            app.accept(step);
        });
    }
}

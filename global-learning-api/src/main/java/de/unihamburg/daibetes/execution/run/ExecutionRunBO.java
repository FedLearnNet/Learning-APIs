package de.unihamburg.daibetes.execution.run;

import bio.cosy.feddb.core.execution.executor.ExecutionStartDTO;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import bio.cosy.feddb.core.execution.orchestrator.DataAnalysisCreatePredictionDTO;
import bio.cosy.feddb.core.execution.run.BaseExecutionRunBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubAO;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;

@ApplicationScoped
public class ExecutionRunBO extends BaseExecutionRunBO<ExecutionRunDTO, ExecutionRunEntity, ExecutionRunAO, ExecutionRunMapper> {

    @Inject
    WorkflowExecutionBO executionBO;

    @Inject
    FederatedAppVersionAO appVersionAO;

    @Inject
    ModelSubAO modelSubAO;

    public ExecutionRunEntity startSingle(ExecutionStartDTO start, DataAnalysisCreatePredictionDTO data,
                                          LinkedHashMap<String, Object> inputs, String keycloakId) {
        HyperParamSearchDTO search = HyperParamSearchDTO.fixed(data.getHyperParams());
        if (data.getModelSubId() == null && data.getAppVersionId() != null) {
            return startTool(start, data, inputs, keycloakId, search);
        }
        return startModel(start, data, inputs, keycloakId, search);
    }

    public ExecutionRunEntity startTool(ExecutionStartDTO start, DataAnalysisCreatePredictionDTO data,
                                        LinkedHashMap<String, Object> inputs, String keycloakId, HyperParamSearchDTO search) {
        FederatedAppVersionEntity tool = appVersionAO.findByIdAuthorChecked(data.getAppVersionId(), keycloakId);
        if (StringUtils.isEmpty(start.getName())) {
            start.setName(tool.getFederatedApp().getName());
        }
        return executionBO.executeSingleTransactional(start, search, inputs,
                step -> step.setFederatedAppVersion(tool));
    }

    public ExecutionRunEntity startModel(ExecutionStartDTO start, DataAnalysisCreatePredictionDTO data,
                                         LinkedHashMap<String, Object> inputs, String keycloakId, HyperParamSearchDTO search) {
        ModelSubEntity subModel = modelSubAO.findByIdAuthorChecked(data.getModelSubId(), keycloakId);
        if (StringUtils.isEmpty(start.getName())) {
            start.setName(subModel.getModelName());
        }
        return executionBO.executeSingleTransactional(start, search, inputs,
                step -> step.setSubModel(subModel));

    }

}
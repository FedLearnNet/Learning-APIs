package de.unihamburg.daibetes.api.workflow.node;

import bio.cosy.feddb.core.api.workflow.WorkflowDTO;
import bio.cosy.feddb.core.api.workflow.base.BaseWorkflowEngine;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubAO;
import de.unihamburg.daibetes.api.model.sub.ModelSubEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import de.unihamburg.daibetes.api.workflow.export.WorkflowNodeExportDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class WorkflowNodeBO extends BaseBo<WorkflowNodeDetailDTO, WorkflowNodeEntity, WorkflowNodeAO, WorkflowNodeMapper> {

    @Inject
    FederatedAppVersionAO federatedAppVersionAO;

    @Inject
    ModelSubAO modelSubAO;

    private final BaseWorkflowEngine workflowEngine = new BaseWorkflowEngine();

    public WorkflowNodeEntity createEntity(Long workflowId, WorkflowNodeDetailDTO dto) {
        dto.setWorkflowId(workflowId);
        WorkflowNodeEntity entity = mapper.dtoToEntity(dto);
        if (dto.getFederatedAppVersionId() != null) {
            Optional<FederatedAppVersionEntity> app = federatedAppVersionAO.findByIdOptional(dto.getFederatedAppVersionId());
            if (app.isEmpty()) {
                throw new IllegalArgumentException("Federated App Version with ID " + dto.getFederatedAppVersionId() + " not found");
            }
            entity.setFederatedAppVersion(app.get());
        }
        if (dto.getModelSubId() != null) {
            Optional<ModelSubEntity> modelSub = modelSubAO.findByIdOptional(dto.getModelSubId());
            if (modelSub.isEmpty()) {
                throw new IllegalArgumentException("Federated App Version with ID " + dto.getFederatedAppVersionId() + " not found");
            }
            entity.setSubModel(modelSub.get());
        }
        ao.persist(entity);
        return entity;
    }

    public WorkflowNodeEntity createEntity(WorkflowEntity workflow, WorkflowNodeExportDTO dto) {
        WorkflowNodeEntity entity = mapper.exportDtoToEntity(dto);
        entity.setWorkflow(workflow);
        boolean added = false;
        if (dto.getModelSubPublishHash() != null) {
            Optional<ModelSubEntity> modelSub = modelSubAO.findByPublishHash(dto.getModelSubPublishHash(), dto.getAppUniqueId());
            if (modelSub.isEmpty()) {
                throw new IllegalArgumentException("Model Sub with publishHash " + dto.getModelSubPublishHash() + " not found");
            }
            added = true;
            entity.setSubModel(modelSub.get());
        } else if (dto.getAppUniqueId() != null) {
            Optional<FederatedAppVersionEntity> app = StringUtils.isEmpty(dto.getVersionPublishHash()) ?
                    federatedAppVersionAO.findLastVersionByAppIdOptional(dto.getAppUniqueId()) :
                    federatedAppVersionAO.findByPublishHash(dto.getVersionPublishHash(), dto.getAppUniqueId());
            if (app.isEmpty()) {
                throw new IllegalArgumentException("Federated App Version with publishHash " + dto.getVersionPublishHash() + " not found");
            }
            added = true;
            entity.setFederatedAppVersion(app.get());
        }
        if (!added) {
            throw new IllegalArgumentException("Either versionPublishHash or modelSubPublishHash must be provided");
        }
        ao.persist(entity);
        return entity;
    }

    public Set<WorkflowNodeEntity> create(Long workflowId, List<WorkflowNodeDetailDTO> nodes) {
        return nodes.stream().map(n -> createEntity(workflowId, n)).collect(Collectors.toSet());
    }


    public WorkflowNodeEntity updateEntity(WorkflowNodeDetailDTO dto) {
        return super.updateEntity(dto);
    }

    public WorkflowNodeEntity createOrUpdate(Long workflowId, WorkflowNodeDetailDTO dto) {
        if (dto.getId() == null) {
            return createEntity(workflowId, dto);
        } else {
            return updateEntity(dto);
        }
    }

    public Set<WorkflowNodeEntity> createOrUpdate(WorkflowDTO dto) {
        if (dto.getId() == null) {
            return Set.of();
        }
        workflowEngine.setExecutionOrder(dto);
        Set<WorkflowNodeEntity> nodes = dto.getNodes().stream()
                .map(n -> createOrUpdate(dto.getId(), n))
                .collect(Collectors.toSet());
        List<Long> nodeIds = nodes.stream().map(WorkflowNodeEntity::getId).toList();
        ao.deleteByWorkflowAndNegativeIds(dto.getId(), nodeIds);
        ao.flush();
        return nodes;
    }

    public Set<WorkflowNodeEntity> clone(WorkflowDTO dto) {
        if (dto.getId() == null) {
            return Set.of();
        }
        Set<WorkflowNodeEntity> nodes = dto.getNodes().stream()
                .peek(this::setBaseValuesNull)
                .map(n -> createOrUpdate(dto.getId(), n))
                .collect(Collectors.toSet());
        ao.flush();
        return nodes;
    }

}

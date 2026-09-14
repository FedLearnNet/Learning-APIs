package de.unihamburg.daibetes.api.app.config;

import bio.cosy.feddb.core.api.app.FederatedAppType;
import bio.cosy.feddb.core.api.app.config.ToolConfigsDTO;
import bio.cosy.feddb.core.base.BaseDTO;
import de.unihamburg.daibetes.api.app.FederatedAppAO;
import de.unihamburg.daibetes.api.app.config.hyperparam.FederatedAppHyperParamConfigBO;
import de.unihamburg.daibetes.api.app.config.hyperparam.FederatedAppHyperParamConfigEntity;
import de.unihamburg.daibetes.api.app.config.input.FederatedAppInputConfigBO;
import de.unihamburg.daibetes.api.app.config.input.FederatedAppInputConfigEntity;
import de.unihamburg.daibetes.api.app.config.output.FederatedAppOutputConfigBO;
import de.unihamburg.daibetes.api.app.config.output.FederatedAppOutputConfigEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;


@ApplicationScoped
public class FederatedAppConfigBO {

    @Inject
    FederatedAppOutputConfigBO outputConfigBO;

    @Inject
    FederatedAppInputConfigBO inputConfigBO;

    @Inject
    FederatedAppHyperParamConfigBO hyperParamConfigBO;

    public ToolConfigsDTO findByAppVersionId(Long appVersionId) {
        ToolConfigsDTO dto = new ToolConfigsDTO();
        if (appVersionId == null) {
            return dto;
        }
        dto.setInput(inputConfigBO.getAll(appVersionId));
        dto.setOutput(outputConfigBO.getAll(appVersionId));
        dto.setHyperparams(hyperParamConfigBO.getAll(appVersionId));
        return dto;
    }

    public ToolConfigsDTO create(FederatedAppVersionEntity versionEntity, FederatedAppType type) {
        try {
            ToolConfigsDTO dto = ToolConfigDefaults.forType(type);
            dto.setInput(inputConfigBO.create(versionEntity, dto.getInput()));
            dto.setOutput(outputConfigBO.create(versionEntity, dto.getOutput()));
            dto.setHyperparams(hyperParamConfigBO.create(versionEntity, dto.getHyperparams()));
            return dto;
        } catch (IllegalArgumentException e) {
            Log.errorf("Failed to persist tool configs for app version id %d: App version not found", versionEntity.getId(), e);
            throw new BadRequestException(e);
        }
    }

    public ToolConfigsDTO persist(ToolConfigsDTO dto, Long appVersionId, FederatedAppType appType) {
        if (dto == null) {
            return null;
        }
        try {
            dto.setInput(inputConfigBO.createOrUpdate(appVersionId, dto.getInput(), appType));
            dto.setOutput(outputConfigBO.createOrUpdate(appVersionId, dto.getOutput(), appType));
            dto.setHyperparams(hyperParamConfigBO.createOrUpdate(appVersionId, dto.getHyperparams()));
        } catch (IllegalArgumentException e) {
            Log.errorf("Failed to persist tool configs for app version id %d: App version not found", appVersionId, e);
            throw new BadRequestException(e);
        }
        return dto;
    }

    public ToolConfigsDTO create(ToolConfigsDTO dto, FederatedAppVersionEntity versionEntity) {
        if (dto == null) {
            return null;
        }
        Log.infof("Creating tool configs for app version id %d", versionEntity.getId());
        try {
            dto.setInput(inputConfigBO.create(versionEntity, dto.getInput()));
            dto.setOutput(outputConfigBO.create(versionEntity, dto.getOutput()));
            dto.setHyperparams(hyperParamConfigBO.create(versionEntity, dto.getHyperparams()));
        } catch (IllegalArgumentException e) {
            Log.errorf("Failed to persist tool configs for app version id %d: App version not found", versionEntity.getId(), e);
            throw new BadRequestException(e);
        }
        return dto;
    }

    public ToolConfigsDTO setBaseValuesNull(ToolConfigsDTO dto) {
        if (dto == null) {
            return null;
        }
        dto.setInput(dto.getInput().stream().peek(this::setBaseValuesNull).toList());
        dto.setOutput(dto.getOutput().stream().peek(this::setBaseValuesNull).toList());
        dto.setHyperparams(dto.getHyperparams().stream().peek(this::setBaseValuesNull).toList());
        return dto;
    }

    public void setBaseValuesNull(BaseDTO dto) {
        dto.setCreatedAt(null);
        dto.setUpdatedAt(null);
        dto.setId(null);
        dto.setVersion(null);
    }

    public void publish(FederatedAppVersionEntity existingVersion, FederatedAppVersionEntity newVersion) {
        Set<FederatedAppOutputConfigEntity> newOutputConfigs = new HashSet<>();
        for (FederatedAppOutputConfigEntity existingOutputConfig : existingVersion.getOutputConfig()) {
            newOutputConfigs.add(outputConfigBO.cloneEntity(existingOutputConfig, newVersion));
        }
        newVersion.setOutputConfig(newOutputConfigs);

        Set<FederatedAppInputConfigEntity> newInputConfigs = new HashSet<>();
        for (FederatedAppInputConfigEntity existingInputConfig : existingVersion.getInputConfig()) {
            newInputConfigs.add(inputConfigBO.cloneEntity(existingInputConfig, newVersion));
        }
        newVersion.setInputConfig(newInputConfigs);

        // HyperParam Config
        Set<FederatedAppHyperParamConfigEntity> newHyperParamConfigs = new HashSet<>();
        for (FederatedAppHyperParamConfigEntity existingHyperParamConfig : existingVersion.getHyperParamConfig()) {
            newHyperParamConfigs.add(hyperParamConfigBO.cloneEntity(existingHyperParamConfig, newVersion));
        }
        newVersion.setHyperParamConfig(newHyperParamConfigs);
    }
}

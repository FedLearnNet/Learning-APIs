package de.unihamburg.daibetes.api.model;

import bio.cosy.feddb.core.api.app.FederatedAppVersionDTO;
import bio.cosy.feddb.core.api.model.*;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.FederatedAppBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.model.access.ModelAccessAO;
import de.unihamburg.daibetes.api.model.access.ModelAccessBO;
import de.unihamburg.daibetes.api.model.access.ModelAccessEntity;
import de.unihamburg.daibetes.api.model.sub.ModelSubBO;
import bio.cosy.feddb.core.api.model.ModelSubDataDTO;
import de.unihamburg.daibetes.api.model.version.ModelVersionBO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;

import java.util.*;
import java.util.stream.Collectors;


@ApplicationScoped
public class ModelBO extends BaseBo<ModelDTO, ModelEntity, ModelAO, ModelMapper> {

    @Inject
    ModelAccessAO accessAO;

    @Inject
    ModelAccessBO accessBO;

    @Inject
    ModelVersionBO modelVersionBO;

    @Inject
    ModelSubBO modelSubBO;

    @Inject
    FederatedAppBO federatedAppBO;

    @Inject
    FederatedAppVersionAO federatedAppVersionAO;

    public List<ModelDTO> getAll(String keycloakId) {
        List<ModelAccessEntity> accessModels = accessAO.getAllAccessibleModel(keycloakId);

        Set<ModelEntity> entities = new HashSet<>(ao.getAllPublished());
        entities.addAll(accessModels.stream()
                .map(ModelAccessEntity::getModel)
                .collect(Collectors.toSet()));
        return new ArrayList<>(mapper.entitiesToDtos(new ArrayList<>(entities)));
    }

    public List<ModelDTO> getAllPublished() {
        return mapper.entitiesToDtos(ao.getAllPublished());
    }

    public List<ModelDTO> getMyModels(String keycloakId, Long appId, Long experimentId, Long federatedExperimentId) {
        List<ModelAccessEntity> ownedModels = accessAO.getAllOwnedModel(keycloakId);
        return ownedModels.stream()
                .map(ModelAccessEntity::getModel)
                .filter(model -> appId == null ||
                        model.getFederatedAppVersion().getFederatedApp().getId().equals(appId))
                .filter(model -> matchesExperimentFilters(model, experimentId, federatedExperimentId))
                .map(mapper::entityToDto)
                .sorted(Comparator.comparing(ModelDTO::getId))
                .toList();
    }

    public ModelDetailDTO findByIdOwner(Long id) {
        ModelDTO modelDTO = mapper.entityToDto(ao.findById(id));
        ModelDetailDTO detailDTO = mapper.toDetail(modelDTO);
        detailDTO.setAccesses(accessBO.getAllForModel(id));
        detailDTO.setCreatedByUser(true);
        detailDTO.setModelVersions(modelVersionBO.getAllForModel(modelDTO.getId()));
        detailDTO.setFederatedApp(federatedAppBO.dtoToDetail(detailDTO.getFederatedApp()));
        return detailDTO;

    }

    public Optional<ModelDTO> getByAppVersionId(Long id) {
        return ao.findByVersionIdAndPublished(id)
                .map(mapper::entityToDto);
    }

    public ModelDetailDTO getBySubId(Long id, String keycloakId) {
        ModelSubDTO subDTO = modelSubBO.getStoreById(id);
        if (subDTO == null) {
            throw new NotFoundException("Model Sub: " + id + " not found");
        }
        Long versionId = subDTO.getModelVersionId();
        ModelDetailDTO detail = getById(subDTO.getModelId(), keycloakId);
        Optional<ModelVersionDTO> foundVersion = detail.getModelVersions().stream().filter(v -> Objects.equals(v.getId(), versionId)).findFirst();
        if (foundVersion.isEmpty()) {
            throw new NotFoundException("Model Version: " + versionId + " not found in Model: " + detail.getId());
        }
        ModelVersionDTO version = foundVersion.get();
        version.setSelectedSubModel(subDTO);
        version.setSubModels(Collections.singletonList(subDTO));
        detail.setModelVersions(Collections.singletonList(version));
        detail.setLastVersion(version);
        return detail;
    }

    public ModelDetailDTO getById(Long id, String keycloakId) {
        if (accessBO.hasUserAnyRights(keycloakId, id)) {
            ModelDTO modelDTO = mapper.entityToDto(ao.findById(id));
            ModelDetailDTO detailDTO = mapper.toDetail(modelDTO);
            detailDTO.setFederatedApp(federatedAppBO.dtoToDetail(detailDTO.getFederatedApp()));
            if (accessBO.isUserOwner(keycloakId, id)) {
                detailDTO.setAccesses(accessBO.getAllForModel(id));
                detailDTO.setCreatedByUser(true);
                detailDTO.setModelVersions(modelVersionBO.getAllForModel(modelDTO.getId()));

            } else {
                detailDTO.setModelVersions(modelVersionBO.getPublicForModel(modelDTO.getId()));
            }

            detailDTO.setCreator(accessBO.getCreator(id));

            return detailDTO;
        }
        throw new NotAllowedException("User does not have access to this model");
    }

    public void deleteById(Long id, String keycloakId) {
        if (accessBO.isUserOwner(keycloakId, id)) {
            ao.deleteById(id);
            return;
        }
        throw new NotAllowedException("User does not have access to this model");
    }

    public ModelDTO update(Long id, ModelDTO request, String keycloakId) {
        if (accessBO.hasUserAnyRights(keycloakId, id)) {
            return update(id, request);
        }
        throw new NotAllowedException("User does not have access to this model");
    }

    public ModelDTO createInitial(Long versionId, String name) {
        ModelDTO newModel = new ModelDTO();
        newModel.setFederatedAppVersionId(versionId);
        newModel.setName(name);
        return create(newModel);
    }

    public ModelVersionDTO create(Long versionId, Long experimentId, Long appId, ModelSubDataDTO model) {
        return create(versionId, experimentId, appId, model.getName());
    }

    public ModelVersionDTO create(Long versionId, Long experimentId, Long appId, String name) {
        ModelDTO newModel = createInitial(versionId, name);
        accessBO.create(appId, newModel.getId());
        return modelVersionBO.createInitial(newModel.getId(), experimentId);
    }

    public ModelVersionDTO createForImage(ModelCreateForImageDTO request, String keycloakId) {
        if (!accessBO.hasUserAnyRights(keycloakId, request.getFederatedAppId())) {
            throw new NotAllowedException("User does not have access to this app");
        }

        ModelDTO newModel = createInitial(request.getFederatedAppVersionId(), request.getName());
        accessBO.create(request.getFederatedAppId(), newModel.getId());
        return modelVersionBO.createInitial(newModel.getId());
    }

    public void createForPublish(FederatedAppVersionDTO version) {
        ModelDTO newModel = createInitial(version.getId(), version.getImageName() + "-model (" + version.getVersion() + ")");
        accessBO.create(version.getFederatedAppId(), newModel.getId());
        modelVersionBO.createInitial(newModel.getId());
    }

    public ModelDTO saveModel(Long versionId, Long experimentId, Long appId, ModelSubDataDTO model, String keycloakId) {

        ModelVersionDTO modelVersion = ao.findByVersionIdAndPublished(versionId)
                .map(modelEntity -> {
                    return modelVersionBO.findLastUnpublishedVersionByExperimentId(experimentId)
                            .orElseGet(() -> modelVersionBO.createInitial(modelEntity.getId(), experimentId));
                }).orElseGet(() -> create(versionId, experimentId, appId, model));

        ModelSubDTO sub = modelSubBO.update(model, modelVersion.getModelId());
        modelSubBO.addModelSubData(sub, model, keycloakId);
        return findByIdOwner(sub.getModelVersionId());
    }

    private boolean matchesExperimentFilters(ModelEntity model, Long experimentId, Long federatedExperimentId) {
        if (experimentId == null && federatedExperimentId == null) {
            return true;
        }
        return Optional.ofNullable(model.getVersions()).orElseGet(Collections::emptySet)
                .stream()
                .flatMap(v -> Optional.ofNullable(v.getSubModels()).orElseGet(Collections::emptySet).stream())
                .anyMatch(sub ->
                        (experimentId == null || (sub.getExperimentRun() != null && Objects.equals(sub.getExperimentRun().getId(), experimentId))) &&
                                (federatedExperimentId == null || (sub.getFederatedExperiment() != null && Objects.equals(sub.getFederatedExperiment().getId(), federatedExperimentId)))
                );
    }

    public void createExternal(ModelDetailDTO external) {
        Log.infof("Creating external model with name %s", external.getName());
        if (external.getUniqueModelId() == null) {
            Log.infof("External app %s has no unique app id, skipping", external.getName());
            return;
        }

        Optional<ModelEntity> existingAppOptional = ao.findByUniqueModelId(external.getUniqueModelId());

        setBaseValuesNull(external);
        ModelEntity entity;
        if (existingAppOptional.isEmpty()) {
            entity = mapper.dtoToEntity(external);
            Optional<String> publishHashOpt = external.getFederatedApp()
                    .getVersions()
                    .stream()
                    .filter(v -> v.getId().equals(external.getFederatedAppVersionId()))
                    .findFirst()
                    .map(FederatedAppVersionDTO::getPublishHash);
            if (publishHashOpt.isEmpty()) {
                Log.warnf("No publish hash found for external app %s with version id %d, skipping", external.getFederatedApp().getName(), external.getFederatedAppVersionId());
                return;
            }
            Optional<FederatedAppVersionEntity> appVersionOpt = federatedAppVersionAO.findByPublishHash(publishHashOpt.get(), external.getFederatedApp().getUniqueAppId());
            if (appVersionOpt.isEmpty()) {
                Log.warnf("No app version found for external app %s with publish hash %s, skipping", external.getFederatedApp().getName(), publishHashOpt.get());
                return;
            }
            entity.setFederatedAppVersion(appVersionOpt.get());
            ao.persist(entity);
        } else {
            //entity = existingAppOptional.get();
            Log.infof("External app with unique id %s already exists", external.getUniqueModelId());
            return;
        }

        Long newModelId = entity.getId();

        for (ModelAccessDTO author : external.getAccesses()) {
            author.setModelId(newModelId);
            accessBO.create(author);
        }
        //Save basic config for new app version
        for (ModelVersionDTO version : external.getModelVersions()) {
            String modelVersionName = external.getName() + " (" + version.getModelVersion() + ")";
            version.setModelId(newModelId);
            setBaseValuesNull(version);
            try {
                modelVersionBO.create(version);

                for (ModelSubDTO sub : version.getSubModels()) {
                    sub.setModelVersionId(version.getId());
                    setBaseValuesNull(sub);
                    modelSubBO.update(sub);
                }
            } catch (Exception e) {
                Log.warnf("Failed to create external model version %s: %s", modelVersionName, e.getMessage());
            }

        }
        Log.infof("External app with name %s created successfully", external.getName());

    }
}

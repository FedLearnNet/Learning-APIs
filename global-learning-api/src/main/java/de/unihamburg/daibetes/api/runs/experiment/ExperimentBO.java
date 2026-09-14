package de.unihamburg.daibetes.api.runs.experiment;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.model.ModelBO;
import de.unihamburg.daibetes.api.runs.experiment.run.ExperimentRunBO;
import de.unihamburg.daibetes.api.runs.experiment.run.ExperimentRunDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@ApplicationScoped
public class ExperimentBO extends BaseBo<ExperimentDTO, ExperimentEntity, ExperimentAO, ExperimentMapper> {


    @Inject
    FederatedAppAuthorBO authorBO;

    @Inject
    ExperimentRunBO experimentRunBO;

    @Inject
    ModelBO modelBO;

    @Inject
    FederatedAppVersionAO federatedAppVersionAO;

    public List<ExperimentDTO> findByAppId(Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, id)) {
            return mapper.entitiesToDtos(ao.findByAppId(id));
        }
        throw new ForbiddenException();
    }

    public ExperimentDetailDTO getById(Long appId, Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, appId)) {
            ExperimentDTO experiment = mapper.entityToDto(ao.findById(id));
            return toDetail(experiment);
        }
        throw new ForbiddenException();
    }

    public ExperimentDetailDTO toDetail(ExperimentDTO experiment) {
        if (experiment == null) {
            return null;
        }
        ExperimentDetailDTO detail = mapper.toDetail(experiment);
        List<ExperimentRunDTO> runs = experimentRunBO.findByExperimentId(experiment.getId());
        detail.setRuns(runs);
        return detail;
    }

    public List<ExperimentRunDTO> listExperimentRuns(Long id, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, id)) {
            return experimentRunBO.findByExperimentId(id);
        }
        throw new ForbiddenException();
    }

    public ExperimentDetailDTO createExperiment(CreateExperimentDTO createDto, Long appId, String keycloakId) {
        if (!authorBO.isUserAuthor(keycloakId, appId)) {
            throw new ForbiddenException("User is not author of app");
        }

        if (federatedAppVersionAO.findByIdOptional(createDto.getFederatedAppVersionId()).isEmpty()) {
            throw new ForbiddenException("App version does not exist");
        }

        LinkedHashMap<String, List<Object>> hyperParams = createDto.getHyperParams();

        ExperimentDTO toCreate = mapper.createToDto(createDto);
        toCreate.setFederatedAppId(appId);
        toCreate.setFederatedAppVersionId(createDto.getFederatedAppVersionId());
        toCreate.setStatus(RunStatusTypes.INITIALIZED);

        ExperimentDTO created = create(toCreate);

        List<LinkedHashMap<String, Object>> combinations = generateCombinations(hyperParams);
        for (LinkedHashMap<String, Object> combination : combinations) {
            experimentRunBO.create(created.getId(), combination);
        }
        ExperimentDetailDTO detailDto = getById(appId, created.getId(), keycloakId);
        detailDto.setStatus(RunStatusTypes.INITIALIZED);
        return detailDto;
    }

    private List<LinkedHashMap<String, Object>> generateCombinations(LinkedHashMap<String, List<Object>> hyperParams) {
        List<LinkedHashMap<String, Object>> combinations = new ArrayList<>();
        generateCombinationsRecursive(hyperParams, new LinkedHashMap<>(), combinations);
        return combinations;
    }

    private void generateCombinationsRecursive(LinkedHashMap<String, List<Object>> hyperParams, LinkedHashMap<String, Object> currentCombination, List<LinkedHashMap<String, Object>> combinations) {
        if (currentCombination.size() == hyperParams.size()) {
            combinations.add(new LinkedHashMap<>(currentCombination));
            return;
        }

        String currentKey = new ArrayList<>(hyperParams.keySet()).get(currentCombination.size());
        List<Object> values = hyperParams.get(currentKey);

        for (Object value : values) {
            currentCombination.put(currentKey, value);
            generateCombinationsRecursive(hyperParams, currentCombination, combinations);
            currentCombination.remove(currentKey);
        }
    }

    public ExperimentDetailDTO update(ExperimentDetailDTO dto, Long appId, String keycloakId) {
        if (authorBO.isUserAuthor(keycloakId, appId)) {
            ExperimentDTO updated = update(dto.getId(), dto.getVersion(), mapper.detailToEntity(dto));
            return toDetail(updated);
        }
        throw new ForbiddenException();
    }
}

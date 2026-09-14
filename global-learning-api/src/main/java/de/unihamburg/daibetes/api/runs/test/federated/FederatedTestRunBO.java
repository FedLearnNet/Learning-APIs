package de.unihamburg.daibetes.api.runs.test.federated;

import bio.cosy.feddb.core.api.run.OutputRunDataDTO;
import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantBO;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantCreateDTO;
import de.unihamburg.daibetes.api.runs.test.federated.participant.FederatedParticipantDTO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FederatedTestRunBO extends BaseBo<FederatedTestRunDTO, FederatedTestRunEntity, FederatedTestRunAO, FederatedTestRunMapper> {

    @Inject
    FederatedAppAuthorBO authorBO;

    @Inject
    FederatedParticipantBO federatedParticipantBO;

    public List<FederatedTestRunDTO> findByAppId(Long appId, String keycloakId) {
        if (!authorBO.isUserAuthor(keycloakId, appId)) {
            throw new ForbiddenException();
        }
        return mapper.entitiesToDtos(ao.findByAppId(appId));
    }

    public FederatedTestRunDTO getByIdAuth(Long appId, Long id, String keycloakId) {
        if (!authorBO.isUserAuthor(keycloakId, appId)) {
            throw new ForbiddenException();
        }
        FederatedTestRunEntity entity = ao.findById(id);
        if (entity == null) {
            throw new NotFoundException("FederatedTestRun not found: " + id);
        }
        return mapper.entityToDto(entity);
    }

    public FederatedTestRunDTO startFederatedTest(Long appId, FederatedTestRunCreateDTO createDTO) {
        FederatedTestRunDTO dto = mapper.createDtoFromRequest(appId, createDTO);
        FederatedTestRunDTO created = create(dto);

        List<FederatedParticipantDTO> persistedParticipants = new ArrayList<>();
        if (createDTO.getParticipants() != null) {
            for (FederatedParticipantCreateDTO pCreate : createDTO.getParticipants()) {
                persistedParticipants.add(federatedParticipantBO.create(pCreate, created.getId()));
            }
        }
        created.setParticipants(persistedParticipants);
        return created;
    }

    public FederatedTestRunDTO updateRun(FederatedTestRunDTO dto) {
        if (dto.getId() == null) {
            throw new NotFoundException("FederatedTestRun id is required");
        }

        boolean updated;
        if (dto.getStatus().equals(RunStatusTypes.FINISHED)) {
            updated = ao.finishRun(
                    dto.getId(),
                    dto.getError(),
                    dto.getCurrentRound()
            );
        } else {
            updated = ao.updateRun(
                    dto.getId(),
                    dto.getStatus(),
                    dto.getError(),
                    dto.getCurrentRound()
            );
        }
        if (!updated) {
            throw new NotFoundException("FederatedTestRun not found: " + dto.getId());
        }
        return getById(dto.getId());
    }

    public FederatedTestRunDTO uploadData(OutputRunDataDTO finishTest) {
        Optional<FederatedTestRunEntity> runEntityOptional = ao.findByIdOptional(finishTest.getRunId());
        if (runEntityOptional.isEmpty()) {
            throw new NotFoundException("Run not found");
        }
        FederatedTestRunEntity runEntity = runEntityOptional.get();
        LinkedHashMap<String, Object> outputData = runEntity.getOutputData();
        if (outputData == null || outputData.isEmpty()) {
            outputData = finishTest.getOutputData();
        } else {
            outputData.putAll(finishTest.getOutputData());
        }
        ao.uploadOutput(finishTest.getRunId(), outputData);
        return getById(finishTest.getRunId());
    }


    public List<FederatedTestRunDTO> closeAllRunningOnDisconnect(Long appId) {
        List<FederatedTestRunEntity> running = ao.findAllRunningByAppId(appId);
        List<FederatedTestRunDTO> result = new ArrayList<>();
        for (FederatedTestRunEntity entity : running) {
            entity.setStatus(RunStatusTypes.ERROR);
            entity.setError("AppEngine disconnected during federated run");
            ao.persist(entity);
            result.add(mapper.entityToDto(entity));
        }
        return result;
    }
}

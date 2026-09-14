package de.unihamburg.daibetes.api.runs.test.federated.participant;

import bio.cosy.feddb.core.base.BaseBo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class FederatedParticipantBO extends BaseBo<FederatedParticipantDTO, FederatedParticipantEntity, FederatedParticipantAO, FederatedParticipantMapper> {

    public List<FederatedParticipantDTO> listParticipants(Long runId) {
        return mapper.entitiesToDtos(ao.findByRun(runId));
    }

    public Optional<FederatedParticipantDTO> getByRunAndParticipant(Long runId, String participantId) {
        return ao.findByRunAndParticipant(runId, participantId).map(mapper::entityToDto);
    }

    public FederatedParticipantDTO updateParticipant(FederatedParticipantUpdateDTO update) {
        if (update.getFederatedRunId() == null || update.getParticipantId() == null) {
            throw new NotFoundException("FederatedRunId and participantId are required");
        }

        FederatedParticipantDTO existing = getByRunAndParticipant(update.getFederatedRunId(), update.getParticipantId())
                .orElseThrow(() -> new NotFoundException("Participant not found for runId " + update.getFederatedRunId() + " and participantId " + update.getParticipantId()));
        boolean updated = ao.updateParticipant(
                existing.getId(),
                update.getStatus(),
                update.getCurrentRound(),
                update.getMessagesReceived(),
                update.getMessagesSent(),
                update.getWaitingFor()
        );
        if (!updated) {
            throw new NotFoundException("Participant not found: " + update.getParticipantId());
        }
        existing.setStatus(update.getStatus());
        existing.setCurrentRound(update.getCurrentRound());
        existing.setMessagesReceived(update.getMessagesReceived());
        existing.setMessagesSent(update.getMessagesSent());
        existing.setWaitingFor(update.getWaitingFor());
        return existing;
    }


    public FederatedParticipantDTO create(FederatedParticipantCreateDTO pCreate, Long runId) {
        FederatedParticipantDTO pDto = mapper.createDtoFromCreateDTO(pCreate, runId);
        return create(pDto);
    }
}

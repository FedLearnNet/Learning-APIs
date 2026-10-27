package de.unihamburg.daibetes.execution.federation;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Optional;

@ApplicationScoped
public class ExecutionFederationAO implements PanacheRepository<ExecutionFederationEntity> {

    /**
     * The relay channel of the step running the workflow node, if the clinic takes part and the relay client id is
     * the one it got for that step; empty once the relay run of the step stopped.
     */
    public Optional<String> findRelayChannelOf(String globalUniqueId, String nodeId, String clinicId, String clientId) {
        return find("from ExecutionFederationEntity f where f.step.run.experiment.globalUniqueId = ?1 and f.step.workflowNode.nodeId = ?2"
                        + " and (f.relayStopped is null or f.relayStopped = false) and exists ("
                        + "from ExecutionParticipantEntity p where p.run = f.step.run and p.participantId = ?3)",
                globalUniqueId, nodeId, clinicId).firstResultOptional()
                // Config and relay client ids are JSON, so they are checked here.
                .filter(federation -> federation.getConfig() != null && federation.getConfig().getChannel() != null)
                .filter(federation -> clientId.equals(federation.getRelayClientIds().get(clinicId)))
                .map(federation -> federation.getConfig().getChannel());
    }

    public void updateCurrentRound(ExecutionFederationEntity federation, Integer currentRound) {
        if (currentRound != null) {
            federation.setCurrentRound(currentRound);
        }
    }
}

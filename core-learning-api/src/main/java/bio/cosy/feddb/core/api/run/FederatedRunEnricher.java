package bio.cosy.feddb.core.api.run;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared logic for turning a {@link FederatedLearningRelayInfoDTO} (one participant's relay
 * credentials) into the participant/config fields a {@link StartRunDTO} needs, regardless of
 * whether that participant is a clinic (local-learning-api) or the platform itself
 * (global-learning-api). {@link FederatedLearningRelayInfoDTO#getCoordinator()} alone determines
 * AGGREGATOR vs CLIENT - callers don't need to special-case which deployment they run in.
 */
public final class FederatedRunEnricher {

    private FederatedRunEnricher() {
    }

    /**
     * Populates participants/config/startAggregator/totalRounds on {@code run} from {@code relay}.
     * {@code run.getHyperParams()}/{@code run.getInputFilePaths()} must already be set - they're
     * copied onto the single participant entry.
     */
    public static void enrichWithFederatedRelay(StartRunDTO run, FederatedLearningRelayInfoDTO relay) {
        boolean isCoordinator = relay.getCoordinator();

        FederatedRunParticipantDTO participant = new FederatedRunParticipantDTO();
        participant.setParticipantId(relay.getId());
        participant.setRole(isCoordinator ? "AGGREGATOR" : "CLIENT");
        participant.setHyperParams(run.getHyperParams());
        participant.setInputFilePaths(run.getInputFilePaths());
        run.setParticipants(List.of(participant));
        run.setStartAggregator(isCoordinator);
        run.setTotalRounds(extractTotalRounds(run.getHyperParams()));

        FederatedRunConfigDTO config = new FederatedRunConfigDTO();
        config.setChannel(relay.getChannel());
        config.setClientId(relay.getId());
        config.setClientKey(relay.getKey());
        config.setRelayKey(relay.getRelayKey());
        config.setCoordinatorId(relay.getCoordinatorId());
        config.setMaxNumClients(relay.getMaxNumClients());
        config.setOrderClientIds(relay.getOrderClientIds() == null ? null : new ArrayList<>(relay.getOrderClientIds()));
        config.setAppVersion(relay.getAppVersion() != null ? relay.getAppVersion().name() : null);
        run.setConfig(config);
    }

    public static Integer extractTotalRounds(Map<String, Object> hyperParams) {
        if (hyperParams == null) {
            return null;
        }
        Object value = hyperParams.getOrDefault("federated_rounds", hyperParams.get("total_rounds"));
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

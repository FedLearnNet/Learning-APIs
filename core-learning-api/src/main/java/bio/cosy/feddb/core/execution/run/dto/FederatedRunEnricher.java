package bio.cosy.feddb.core.execution.run.dto;

import bio.cosy.feddb.core.execution.helper.NodeHyperParams;

import java.util.List;

/**
 * Turns the relay config of one participant into what a federated app reads from its start: the participant, its
 * role and the relay. Used by a clinic and by the platform running the aggregator itself.
 */
public final class FederatedRunEnricher {

    private FederatedRunEnricher() {
    }

    /**
     * Sends exactly one participant, the one starting: all participants run the same app, which only knows itself
     * when it gets a single participant. Hyper parameters and input file paths of the start must be set already.
     *
     * @param coordinator    the participant runs the aggregator
     * @param aggregatorOnly the participant has no data of its own, the app builds no training app for it
     */
    public static void enrichWithFederatedRelay(StartRunDTO start, FederatedRunConfigDTO config, boolean coordinator,
                                                boolean aggregatorOnly) {
        start.setParticipants(List.of(new FederatedRunParticipantDTO(config.getClientId(),
                coordinator ? "AGGREGATOR" : "CLIENT", start.getHyperParams(), start.getInputFilePaths())));
        start.setStartAggregator(coordinator);
        start.setAggregatorOnly(aggregatorOnly);
        start.setTotalRounds(NodeHyperParams.totalRounds(start.getHyperParams()));
        start.setConfig(config);
    }
}

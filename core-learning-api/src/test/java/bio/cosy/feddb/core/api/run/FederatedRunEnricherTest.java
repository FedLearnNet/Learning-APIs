package bio.cosy.feddb.core.api.run;

import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FederatedRunEnricherTest {

    private static FederatedLearningRelayInfoDTO relay(String id, boolean coordinator) {
        FederatedLearningRelayInfoDTO relay = new FederatedLearningRelayInfoDTO();
        relay.setId(id);
        relay.setCoordinator(coordinator);
        relay.setCoordinatorId("platform");
        relay.setChannel("channel-1");
        relay.setOrderClientIds(new LinkedHashSet<>(List.of("clinic-1", "clinic-2")));
        return relay;
    }

    @Test
    void platformAggregatorIsMarkedAggregatorOnly() {
        StartRunDTO run = new StartRunDTO();
        run.setInputFilePaths(new LinkedHashMap<>());

        FederatedRunEnricher.enrichWithFederatedRelay(run, relay("platform", true), true);

        assertEquals(Boolean.TRUE, run.getAggregatorOnly());
        assertEquals(Boolean.TRUE, run.getStartAggregator());
        assertEquals("AGGREGATOR", run.getParticipants().getFirst().getRole());
        assertEquals("platform", run.getConfig().getCoordinatorId());
        assertEquals(List.of("clinic-1", "clinic-2"), run.getConfig().getOrderClientIds());
    }

    @Test
    void clinicClientIsNotAggregatorOnly() {
        StartRunDTO run = new StartRunDTO();

        FederatedRunEnricher.enrichWithFederatedRelay(run, relay("clinic-1", false), false);

        assertEquals(Boolean.FALSE, run.getAggregatorOnly());
        assertEquals(Boolean.FALSE, run.getStartAggregator());
        assertEquals("CLIENT", run.getParticipants().getFirst().getRole());
    }

    @Test
    void totalRoundsStaysUnsetWithoutHyperParam() {
        StartRunDTO run = new StartRunDTO();
        run.setHyperParams(new LinkedHashMap<>());

        FederatedRunEnricher.enrichWithFederatedRelay(run, relay("platform", true), true);

        // Left to the tool, which resolves the count from its own config defaults.
        assertNull(run.getTotalRounds());
    }
}

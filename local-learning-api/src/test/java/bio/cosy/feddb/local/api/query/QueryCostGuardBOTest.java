package bio.cosy.feddb.local.api.query;

import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.core.api.query.QueryRejectedException;
import bio.cosy.feddb.core.api.query.item.ConnectingOperator;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.api.query.item.TemporalRelationDTO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QueryCostGuardBOTest {

    private QueryCostGuardBO guard;

    @BeforeEach
    void setUp() {
        FLNetClientConfig config = mock(FLNetClientConfig.class, RETURNS_DEEP_STUBS);
        when(config.privacy().query().maxConditions()).thenReturn(4);
        when(config.privacy().query().maxTemporalRelations()).thenReturn(1);
        when(config.privacy().query().maxDepth()).thenReturn(3);
        guard = new QueryCostGuardBO();
        guard.config = config;
    }

    @Test
    void acceptsQueryInsideLimits() {
        assertDoesNotThrow(() -> guard.checkShape(and(leaf(), leaf(), and(leaf(), leaf()))));
    }

    @Test
    void rejectsTooManyConditions() {
        assertThrows(QueryRejectedException.class,
                () -> guard.checkShape(and(leaf(), leaf(), leaf(), leaf(), leaf())));
    }

    @Test
    void rejectsTooManyTemporalRelations() {
        QueryItemDTO group = and(leaf(), leaf());
        group.setTemporalRelations(List.of(relation(), relation()));
        assertThrows(QueryRejectedException.class, () -> guard.checkShape(group));
    }

    @Test
    void rejectsDeepNesting() {
        assertThrows(QueryRejectedException.class, () -> guard.checkShape(and(and(and(leaf())))));
    }

    private QueryItemDTO and(QueryItemDTO... conditions) {
        QueryItemDTO group = new QueryItemDTO();
        group.setConnectingOperator(ConnectingOperator.AND);
        List<QueryItemDTO> list = new ArrayList<>();
        Collections.addAll(list, conditions);
        group.setConditions(list);
        return group;
    }

    private QueryItemDTO leaf() {
        QueryItemDTO leaf = new QueryItemDTO();
        leaf.setOntologyId("ontology");
        leaf.setOperator(QueryOperatorTypes.EQUAL);
        leaf.setValue("1");
        return leaf;
    }

    private TemporalRelationDTO relation() {
        TemporalRelationDTO relation = new TemporalRelationDTO();
        relation.setSourceCondition(0);
        relation.setTargetCondition(1);
        return relation;
    }
}

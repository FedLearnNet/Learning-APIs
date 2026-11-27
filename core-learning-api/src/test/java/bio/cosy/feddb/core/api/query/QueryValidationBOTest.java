package bio.cosy.feddb.core.api.query;

import bio.cosy.feddb.core.api.query.item.IntervalPartDTO;
import bio.cosy.feddb.core.api.query.item.IntervalUnit;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.api.query.item.TemporalRelationDTO;
import bio.cosy.feddb.core.api.query.item.TemporalRelationship;
import bio.cosy.feddb.core.api.query.item.TimeAggregationMethod;
import bio.cosy.feddb.core.api.query.item.ValueMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryValidationBOTest {

    private final QueryValidationBO validationBO = new QueryValidationBO();

    @Test
    void emptyQueries() {
        assertTrue(validationBO.isEmptyQuery(null));
        assertTrue(validationBO.isEmptyQuery(QueryItemDTO.andGroup()));
        assertFalse(validationBO.isEmptyQuery(leaf()));
        assertFalse(validationBO.isEmptyQuery(QueryItemDTO.andGroup(leaf())));
    }

    @Test
    void acceptsValidTree() {
        assertDoesNotThrow(() -> validationBO.validateTree(QueryItemDTO.andGroup(leaf())));
    }

    @Test
    void rejectsInvalidLeaf() {
        QueryItemDTO within = leaf();
        within.setTimeAggregationMethod(TimeAggregationMethod.WITHIN);

        QueryRejectedException e = assertThrows(QueryRejectedException.class,
                () -> validationBO.validateTree(QueryItemDTO.andGroup(within)));
        assertTrue(e.getMessage().contains("WITHIN requires valueAggregationMethod AVERAGE"));
    }

    @Test
    void rejectsInvertedBounds() {
        QueryItemDTO bounded = leaf();
        bounded.setAtLeast(3);
        bounded.setAtMost(1);

        assertThrows(QueryRejectedException.class, () -> validationBO.validateTree(QueryItemDTO.andGroup(bounded)));
    }

    @Test
    void rejectsInvalidIntervals() {
        QueryItemDTO missingWindow = leaf();
        missingWindow.setTimeAggregationMethod(TimeAggregationMethod.WITHIN_AT_LEAST);
        missingWindow.setTimeWindowCount(2);
        assertRejected(QueryItemDTO.andGroup(missingWindow), "timeWindow is missing");

        QueryItemDTO zeroDelta = leaf();
        zeroDelta.setValueMode(ValueMode.DELTA);
        zeroDelta.setDeltaInterval(List.of(new IntervalPartDTO(0, IntervalUnit.DAYS)));
        assertRejected(QueryItemDTO.andGroup(zeroDelta), "deltaInterval must be positive");

        TemporalRelationDTO tooLong = new TemporalRelationDTO();
        tooLong.setSourceCondition(0);
        tooLong.setTargetCondition(1);
        tooLong.setRelationship(TemporalRelationship.WITHIN);
        tooLong.setInterval(List.of(new IntervalPartDTO(201, IntervalUnit.YEARS)));
        QueryItemDTO group = QueryItemDTO.andGroup(leaf(), leaf());
        group.setTemporalRelations(List.of(tooLong));
        assertRejected(group, "relation interval exceeds the maximum of 200 years");
    }

    private void assertRejected(QueryItemDTO root, String message) {
        QueryRejectedException e = assertThrows(QueryRejectedException.class, () -> validationBO.validateTree(root));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    private QueryItemDTO leaf() {
        QueryItemDTO leaf = new QueryItemDTO();
        leaf.setOntologyId("2001");
        leaf.setOperator(QueryOperatorTypes.EXISTS);
        return leaf;
    }
}

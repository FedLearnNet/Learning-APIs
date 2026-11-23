package de.unihamburg.daibetes.api.query.response;

import bio.cosy.feddb.core.api.query.QueryClientResponseDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.execution.ExecutionMode;
import de.unihamburg.daibetes.api.query.QueryAO;
import de.unihamburg.daibetes.api.query.QueryBO;
import de.unihamburg.daibetes.api.query.QueryEntity;
import de.unihamburg.daibetes.config.FLNetConfig;
import jakarta.ws.rs.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QueryResponseReleaseTest {

    private static final String GLOBAL_ID = "global-query";

    // answers the mocked AO stored
    private final List<QueryResponseEntity> stored = new ArrayList<>();
    private QueryResponseBO responseBO;
    private QueryBO queryBO;
    private FLNetConfig config;
    private QueryEntity query;

    @BeforeEach
    void setUp() {
        query = new QueryEntity();
        query.setId(1L);
        query.setGlobalUniqueId(GLOBAL_ID);
        query.setHasFired(true);

        queryBO = mock(QueryBO.class);
        QueryAO queryAO = mock(QueryAO.class);
        when(queryAO.findForUpdateByGlobalUniqueId(GLOBAL_ID)).thenReturn(Optional.of(query));

        config = mock(FLNetConfig.class, RETURNS_DEEP_STUBS);
        when(config.query().minQueryResults()).thenReturn(3);
        when(config.query().releaseTimeout()).thenReturn(Duration.ofHours(1));

        responseBO = new QueryResponseBO();
        responseBO.queryBO = queryBO;
        responseBO.queryAO = queryAO;
        responseBO.config = config;
        responseBO.ao = mock(QueryResponseAO.class);
        responseBO.mapper = mock(QueryResponseMapper.class);
        doAnswer(invocation -> stored.add(invocation.getArgument(0))).when(responseBO.ao).persist(any(QueryResponseEntity.class));
        when(responseBO.ao.countByQuery(1L)).thenAnswer(invocation -> (long) stored.size());
        when(responseBO.ao.existsByQueryAndConnection(anyLong(), anyString())).thenAnswer(invocation ->
                stored.stream().anyMatch(answer -> answer.getConnectionId().equals(invocation.getArgument(1))));
        when(responseBO.ao.listMatchedByQuery(1L)).thenAnswer(invocation ->
                stored.stream().filter(answer -> answer.getCount() > 0).toList());
        when(responseBO.mapper.clientToEntity(any())).thenAnswer(invocation -> {
            QueryResponseEntity entity = new QueryResponseEntity();
            entity.setId((long) stored.size() + 10);
            entity.setSupportedModes(((QueryClientResponseDTO) invocation.getArgument(0)).getSupportedModes());
            return entity;
        });
        when(responseBO.mapper.entitiesToDtos(any(Stream.class))).thenAnswer(invocation ->
                ((Stream<?>) invocation.getArgument(0)).map(entity -> new QueryResponseDTO()).toList());
    }

    @Test
    void releasesWhenAllClientsAnswered() {
        query.setExpectedAnswers(3);

        responseBO.saveResponse(response(400L, null), "c1", null);
        responseBO.saveResponse(response(300L, null), "c2", null);
        verify(queryBO, never()).releaseResult(any(), anyInt());

        responseBO.saveResponse(response(200L, null), "c3", null);
        verify(queryBO).releaseResult(query, 900);
    }

    @Test
    void countsRejectionsAndEmptyAnswersAsAnswered() {
        query.setExpectedAnswers(3);

        responseBO.saveResponse(response(400L, null), "c1", null);
        responseBO.saveResponse(QueryClientResponseDTO.createErrorResponse(GLOBAL_ID), "c2", null);
        responseBO.saveResponse(response(0L, null), "c3", null);

        // one client with matches is below the minimum of 3, so the sum is held back
        verify(queryBO).releaseResult(query, 0);
    }

    @Test
    void releasesSumForOneTreClient() {
        query.setExpectedAnswers(1);

        responseBO.saveResponse(response(400L, Set.of(ExecutionMode.TRE)), "c1", null);
        query.setHasResult(true);

        verify(queryBO).releaseResult(query, 400);
        assertEquals("c1", responseBO.findConnectionId(10L, 1L));
    }

    @Test
    void releasesAtDeadlineWithAnswersSoFar() {
        when(config.query().releaseTimeout()).thenReturn(Duration.ofMillis(50));
        query.setExpectedAnswers(3);

        responseBO.releaseAtDeadline(GLOBAL_ID);
        responseBO.saveResponse(response(400L, Set.of(ExecutionMode.TRE)), "c1", null);

        verify(queryBO, timeout(5_000)).releaseResult(query, 400);
    }

    @Test
    void releasesAtDeadlineWithoutClients() {
        query.setExpectedAnswers(0);

        responseBO.releaseTransactional(GLOBAL_ID);

        verify(queryBO).releaseResult(query, 0);
    }

    @Test
    void deadlineSkipsReleasedQuery() {
        query.setHasResult(true);

        responseBO.releaseTransactional(GLOBAL_ID);

        verify(queryBO, never()).releaseResult(any(), anyInt());
    }

    @Test
    void ignoresAnswersAfterRelease() {
        query.setExpectedAnswers(3);
        query.setHasResult(true);

        responseBO.saveResponse(response(300L, null), "c1", null);

        assertTrue(stored.isEmpty());
    }

    @Test
    void ignoresAnswersToUnfiredQuery() {
        query.setHasFired(false);

        responseBO.saveResponse(response(300L, null), "c1", null);

        assertTrue(stored.isEmpty());
    }

    @Test
    void ignoresSecondAnswerOfClient() {
        query.setExpectedAnswers(2);

        responseBO.saveResponse(response(300L, null), "c1", null);
        responseBO.saveResponse(response(300L, null), "c1", null);

        assertEquals(1, stored.size());
        verify(queryBO, never()).releaseResult(any(), anyInt());
    }

    @Test
    void hidesClientsBeforeRelease() {
        query.setExpectedAnswers(2);
        responseBO.saveResponse(response(400L, Set.of(ExecutionMode.TRE)), "c1", null);
        when(queryBO.getById(1L, "user")).thenReturn(new QueryDTO());

        assertTrue(responseBO.getSystems(1L, "user").isEmpty());
        assertThrows(NotFoundException.class, () -> responseBO.findConnectionId(10L, 1L));
    }

    @Test
    void showsClientsAfterRelease() {
        query.setExpectedAnswers(3);
        responseBO.saveResponse(response(400L, null), "c1", null);
        responseBO.saveResponse(response(300L, null), "c2", null);
        responseBO.saveResponse(response(200L, null), "c3", null);
        query.setHasResult(true);
        QueryDTO released = new QueryDTO();
        released.setHasResult(true);
        when(queryBO.getById(1L, "user")).thenReturn(released);

        assertEquals(3, responseBO.getSystems(1L, "user").size());
    }

    private QueryClientResponseDTO response(Long count, Set<ExecutionMode> modes) {
        QueryClientResponseDTO response = QueryClientResponseDTO.createResponse(count, GLOBAL_ID);
        response.setSupportedModes(modes);
        return response;
    }
}

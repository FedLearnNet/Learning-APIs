package unit;

import bio.cosy.feddb.core.api.query.QueryClientResponseDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.core.api.socket.FedDBClientResponseType;
import bio.cosy.feddb.local.api.cohort.permission.PermissionBO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionDTO;
import bio.cosy.feddb.local.api.cohort.queryability.CohortQueryAbilityBO;
import bio.cosy.feddb.local.api.query.QueryBO;
import io.quarkus.test.InjectMock;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class QueryPermissionScopeTest {

    @Inject
    QueryBO queryBO;

    @InjectMock
    PermissionBO permissionBO;

    @InjectMock
    CohortQueryAbilityBO cohortQueryAbilityBO;

    @Test
    @TestTransaction
    void queryabilityRejectionReturnsPermissionDenied() {
        PermissionDTO permission = new PermissionDTO();
        permission.setCohortId(1L);
        when(permissionBO.getAllowedToQueryCohortIds(any())).thenReturn(List.of(permission));
        when(cohortQueryAbilityBO.filterQueryableCohortIds(any(), any())).thenReturn(List.of());

        QueryItemDTO item = new QueryItemDTO();
        item.setOntologyId("2002");
        item.setDataTypeId("1002");
        item.setOperator(QueryOperatorTypes.BIGGER);
        item.setValue("26");

        QueryDTO query = new QueryDTO();
        query.setGlobalUniqueId(UUID.randomUUID().toString());
        query.setKeycloakId("query-ability-user");
        query.setQuery(QueryItemDTO.andGroup(item));

        QueryClientResponseDTO response = queryBO.handleQuery(query);

        assertEquals(FedDBClientResponseType.ERROR, response.getType());
        assertEquals(0L, response.getCount());
    }
}

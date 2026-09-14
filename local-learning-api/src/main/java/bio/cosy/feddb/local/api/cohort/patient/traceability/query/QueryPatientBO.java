package bio.cosy.feddb.local.api.cohort.patient.traceability.query;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.core.base.PagedResponse;
import bio.cosy.feddb.local.api.query.QueryAO;
import bio.cosy.feddb.local.api.query.QueryEntity;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.Collection;
import java.util.List;

@ApplicationScoped
public class QueryPatientBO extends BaseBo<QueryPatientDTO, QueryPatientEntity, QueryPatientAO, QueryPatientMapper> {

    @Inject
    QueryAO queryAO;

    public PagedResponse<QueryPatientDTO> list(
            Page page, Long internalCohortId, Long internalPatientId, Long queryId) {

        List<QueryPatientEntity> foundEntity = ao.list(page, internalCohortId,
                internalPatientId, queryId);
        List<QueryPatientDTO> found = mapper.entitiesToDtos(foundEntity);
        return new PagedResponse<>(found, page.index, page.size);
    }

    @Transactional
    public void storeMatchedPatientsTransactional(String patientMatchSql, Collection<Long> allowedCohortIds,
                                                  Long queryId) {
        if (patientMatchSql == null || patientMatchSql.isBlank()) {
            Log.warn("No patient match SQL for query " + queryId + ", skipping traceability write.");
            return;
        }
        QueryEntity query = queryAO.findById(queryId);
        if (query == null) {
            Log.error("Query with ID " + queryId + " does not exist.");
            return;
        }

        //TODO set lastQueriedAt on the matched patients, also set-based
        int stored = ao.insertMatchedPatients(patientMatchSql, queryId, allowedCohortIds);
        Log.infof("Stored %d matched patients for query %s", stored, queryId);
    }
}

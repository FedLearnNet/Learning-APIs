package de.unihamburg.daibetes.scheduler;

import de.unihamburg.daibetes.api.query.response.QueryResponseBO;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import static io.quarkus.scheduler.Scheduled.ConcurrentExecution.SKIP;

@ApplicationScoped
public class QueryReleaseScheduler {

    @Inject
    QueryResponseBO queryResponseBO;

    @Scheduled(identity = "query-result-release", every = "1s", concurrentExecution = SKIP)
    @Transactional
    void releaseDueQueriesTransactional() {
        queryResponseBO.releaseDueQueries();
    }
}

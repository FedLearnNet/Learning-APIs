package bio.cosy.feddb.local.api.learning.request.metrics;

import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class RequestRunMetricsAO implements PanacheRepository<RequestRunMetricsEntity> {

    public List<RequestRunMetricsEntity> list(Page page, FederatedLearningRequestStatus status) {
        StringBuilder query = new StringBuilder();
        Map<String, Object> params = new HashMap<>();
        if (status != null) {
            query.append("status = :status");
            params.put("status", status);
        }
        return find(query.toString(), params).page(page).list();
    }

    public Optional<RequestRunMetricsEntity> findByGlobalRequestId(UUID globalRequestId) {
        return find("globalRequestId", globalRequestId).firstResultOptional();
    }
}

package bio.cosy.feddb.local.api.search;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class SearchServiceImpl implements SearchService {
    @Inject
    UserIdentity userIdentity;

    @Inject
    SearchBO searchBO;

    @Override
    public List<SearchResultDTO<?>> search(String query, Integer limit) {
        String keycloakId = userIdentity.getKeycloakId();
        return searchBO.search(query, limit, keycloakId);
    }
}

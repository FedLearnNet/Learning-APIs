package bio.cosy.feddb.local.api.learning;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.local.services.GlobalAPIService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class GlobalAppDetailServiceImpl implements GlobalAppDetailService {

    @Inject
    @RestClient
    GlobalAPIService globalAPIClient;

    @Override
    public FederatedAppDetailDTO getAppDetail(Long id) {
        try {
            return globalAPIClient.getApp(id);
        } catch (Exception e) {
            Log.error(e.getMessage());
            return new FederatedAppDetailDTO();
        }
    }
}

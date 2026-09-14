package bio.cosy.feddb.local.services.datamodler;

import bio.cosy.feddb.core.api.datamodler.subscription.SchemaSubscriptionService;
import bio.cosy.feddb.local.services.GlobalAPIAuthRequestFilter;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "global-schema-api")
@RegisterProvider(GlobalAPIAuthRequestFilter.class)
public interface GlobalSchemaSubscriptionService extends SchemaSubscriptionService {
}

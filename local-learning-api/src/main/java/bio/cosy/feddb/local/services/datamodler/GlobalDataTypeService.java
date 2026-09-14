package bio.cosy.feddb.local.services.datamodler;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeService;
import bio.cosy.feddb.local.services.GlobalAPIAuthRequestFilter;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "global-schema-api")
@RegisterProvider(GlobalAPIAuthRequestFilter.class)
public interface GlobalDataTypeService extends DataTypeService {
}

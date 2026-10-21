package de.unihamburg.daibetes.services;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeService;
import io.quarkus.oidc.client.filter.OidcClientFilter;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

// service account token, also used without a user (test runs, exports)
@RegisterRestClient(configKey = "data-modeler-service")
@OidcClientFilter
public interface DataModelerDataTypeService extends DataTypeService {
}

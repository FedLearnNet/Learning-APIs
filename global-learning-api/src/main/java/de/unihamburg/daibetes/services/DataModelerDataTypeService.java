package de.unihamburg.daibetes.services;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeService;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

@RegisterRestClient(configKey = "data-modeler-service")
public interface DataModelerDataTypeService extends DataTypeService {
}

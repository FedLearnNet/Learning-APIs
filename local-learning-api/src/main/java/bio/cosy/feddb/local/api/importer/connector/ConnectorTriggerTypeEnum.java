package bio.cosy.feddb.local.api.importer.connector;

import com.fasterxml.jackson.annotation.JsonAlias;

public enum ConnectorTriggerTypeEnum {
    @JsonAlias({"on_connector_success", "ON_CONNECTOR_SUCCESS"})
    ON_CONNECTOR_SUCCESS
}

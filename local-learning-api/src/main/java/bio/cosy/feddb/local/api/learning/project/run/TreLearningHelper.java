package bio.cosy.feddb.local.api.learning.project.run;

import bio.cosy.feddb.core.api.tre.ExternalDatabaseAccess;
import bio.cosy.feddb.core.api.tre.ExternalDatabaseType;
import bio.cosy.feddb.core.services.orch.dto.StartWorkflowNodeDTO;
import bio.cosy.feddb.local.api.cohort.ExternalDatabaseDTO;
import bio.cosy.feddb.local.api.cohort.ExternalDatabaseLocation;
import bio.cosy.feddb.local.execution.step.ExecutionRunStepEntity;

import java.util.Objects;

public class TreLearningHelper {

    public static final String FORWARDER_HOST = "flnet-db";

    public static void connect(ExternalDatabaseDTO database, StartWorkflowNodeDTO start) {
        ExternalDatabaseType type = Objects.requireNonNullElse(database.getType(), ExternalDatabaseType.OTHER);
        boolean address = database.getLocation() == ExternalDatabaseLocation.ADDRESS;
        start.setExternalSource(address ? database.getHost() + ":" + database.getPort() : database.getHost());
        start.upsertEnv("FLNET_DB_TYPE", type.name());
        start.upsertEnv("FLNET_DB_HOST", address ? FORWARDER_HOST : database.getHost());
        start.upsertEnv("FLNET_DB_PORT", String.valueOf(database.getPort()));
        start.upsertEnv("FLNET_DB_NAME", Objects.requireNonNullElse(database.getDatabase(), ""));
        start.upsertEnv("FLNET_DB_USER", Objects.requireNonNullElse(database.getUsername(), ""));
        start.upsertEnv("FLNET_DB_PASSWORD", Objects.requireNonNullElse(database.getPassword(), ""));
        if (database.getEnv() != null) {
            database.getEnv().forEach(start::upsertEnv);
        }
    }

    public static void checkTool(ExternalDatabaseDTO database, ExecutionRunStepEntity step) {
        if (database.getAccess() == ExternalDatabaseAccess.TOOL
                && !Objects.equals(database.getToolVersionId(), step.getWorkflowNode().getFederatedAppVersionId())) {
            throw new IllegalStateException("Only " + database.getToolName() + " may read the external database");
        }
    }
}

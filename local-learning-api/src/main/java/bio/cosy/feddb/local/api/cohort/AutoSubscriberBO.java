package bio.cosy.feddb.local.api.cohort;

import bio.cosy.feddb.core.helper.ResourceLoader;
import bio.cosy.feddb.local.api.cohort.permission.PermissionBO;
import bio.cosy.feddb.local.api.cohort.permission.PermissionDTO;
import bio.cosy.feddb.local.api.importer.ImporterDataFileWatcherBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.files.upload.ConnectorFileUploadBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunBO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AutoSubscriberBO {

    @Inject
    CohortBO cohortBO;

    @Inject
    CohortAO cohortAO;

    @Inject
    PermissionBO permissionBO;

    @Inject
    ConnectorBO connectorBO;

    @Inject
    ConnectorRunBO connectorRunBO;

    @Inject
    ImporterDataFileWatcherBO etlDataFileWatcherBO;

    @Inject
    ConnectorFileUploadBO uploadBO;

    @Inject
    FLNetClientConfig config;

    public Uni<Boolean> subscribeToFLNet(Map<String, FLNetClientConfig.AutoSubscribeGroupConfig> groups) {
        if (groups == null || groups.isEmpty()) {
            Log.info("AutoSubscribe: no groups configured, nothing to do");
            return Uni.createFrom().item(true);
        }
        // The groups run one after another on a single worker thread, so the main
        // startup thread is not blocked but the subscriptions do not race each other.
        //
        // Subscribing resolves the schema's ontologies and datatypes through a
        // check-then-insert (see OntologyBO#fetchOrCreate). Two groups typically share
        // most of those — in the reference deployment the US-130 and the MIMIC-IV
        // schema are the same fifty variables — so running them concurrently makes two
        // transactions insert the same ontology.global_id and deadlock in PostgreSQL,
        // failing whichever group loses the race. Sequential subscription lets the
        // second group find what the first one created.
        return Uni.createFrom().emitter(emitter ->
                Infrastructure.getDefaultExecutor().execute(() -> {
                    boolean allSucceeded = true;
                    for (Map.Entry<String, FLNetClientConfig.AutoSubscribeGroupConfig> entry : groups.entrySet()) {
                        try {
                            allSucceeded &= runSubscribe(entry.getKey(), entry.getValue());
                        } catch (Throwable throwable) {
                            Log.errorf("AutoSubscribe '%s' failed: %s", entry.getKey(), throwable.getMessage(),
                                    throwable);
                            allSucceeded = false;
                        }
                    }
                    emitter.complete(allSucceeded);
                }));
    }

    public Uni<Boolean> subscribeToFLNet(String name, FLNetClientConfig.AutoSubscribeGroupConfig groupConfig) {
        return Uni.createFrom().emitter(emitter ->
                Infrastructure.getDefaultExecutor().execute(() -> {
                    try {
                        emitter.complete(runSubscribe(name, groupConfig));
                    } catch (Throwable throwable) {
                        Log.errorf("AutoSubscribe '%s' failed: %s", name, throwable.getMessage(), throwable);
                        emitter.complete(false);
                    }
                }));
    }

    @ActivateRequestContext
    public boolean runSubscribe(String name, FLNetClientConfig.AutoSubscribeGroupConfig groupConfig) {
        Log.infof("AutoSubscribe '%s': starting", name);
        String keycloakId = groupConfig.addForUser().orElseGet(() -> config.user().systemUserName());
        ConnectorDTO connector = prepareSubscription(name, groupConfig, keycloakId);

        if (connector != null) {
            startConnectorRunAndWatch(name, groupConfig, connector, keycloakId);
        }

        Log.infof("AutoSubscribe '%s': done", name);
        return true;
    }

    public ConnectorDTO prepareSubscription(String name,
                                            FLNetClientConfig.AutoSubscribeGroupConfig groupConfig,
                                            String keycloakId) {
        Long cohortId = ensureCohort(name, groupConfig, keycloakId);

        return groupConfig.connectorPath()
                .map(connectorPath -> ensureConnector(name, groupConfig, cohortId, connectorPath, keycloakId))
                .orElse(null);
    }

    private void startConnectorRunAndWatch(String name,
                                           FLNetClientConfig.AutoSubscribeGroupConfig groupConfig,
                                           ConnectorDTO connector,
                                           String keycloakId) {
        if (Boolean.TRUE.equals(groupConfig.fileWatching())) {
            groupConfig.etlDataPath().ifPresent(
                    etlDataPath -> etlDataFileWatcherBO.watch(name, etlDataPath, connector.getId(), keycloakId));
        }

        if (!hasRunnableInput(connector)) {
            Log.warnf(
                    "AutoSubscribe '%s': skipping run for connector %d because no input file is attached",
                    name, connector.getId());
            return;
        }

        Log.infof("AutoSubscribe '%s': starting run for connector %d", name, connector.getId());
        connectorRunBO.startRun(connector.getId(), true, false, keycloakId);
    }

    private boolean hasRunnableInput(ConnectorDTO connector) {
        if (connector.getInputConfig() instanceof FileUploadSettingsDTO fileSettings) {
            return fileSettings.getFileId() != null;
        }
        return true;
    }

    @Transactional
    public Long ensureCohort(String name,
                             FLNetClientConfig.AutoSubscribeGroupConfig groupConfig,
                             String keycloakId) {
        Long cohortId;
        Optional<CohortEntity> existingCohort = cohortAO.findByName(groupConfig.cohortName());
        if (existingCohort.isPresent()) {
            // Cohort already exists — skip subscribe / cohort creation / permission setup
            // (steps 1–4).
            cohortId = existingCohort.get().getId();
            Log.infof("AutoSubscribe '%s': cohort '%s' (id=%d) already exists, skipping schema subscribe and permission setup",
                    name, groupConfig.cohortName(), cohortId);
        } else {
            // Subscribe to the global schema by id and create the cohort named after the
            // schema.
            CreateCohortDTO createDTO = new CreateCohortDTO();
            createDTO.setName(groupConfig.cohortName());
            createDTO.setGlobalSchemaID(groupConfig.globalSchemaId());
            CohortDTO cohort = cohortBO.create(createDTO, keycloakId, false);
            cohortId = cohort.getId();
            Log.infof("AutoSubscribe '%s': cohort %d created", name, cohortId);

            // Apply permission directly from configuration (with the configured defaults).
            PermissionDTO permission = new PermissionDTO();
            permission.setCohortId(cohortId);
            if (groupConfig.useDefaultCohortPermission()) {
                permissionBO.setDefault(permission, config.cohort().defaultCohortPermission());
            } else {
                permissionBO.setDefault(permission, groupConfig.defaultCohortPermission());
            }
            permissionBO.create(permission);
            Log.infof("AutoSubscribe '%s': permission applied to cohort %d", name, cohortId);
        }
        return cohortId;
    }

    /**
     * Creating the connector also uploads the configured ETL file into a PostgreSQL
     * large object, which happens inside this transaction. Real source archives are
     * large — full MIMIC-IV is several gigabytes — and streaming one into the database
     * takes far longer than the default 60-second transaction timeout, after which the
     * reaper aborts the transaction mid-write and the subscription fails.
     * <p>
     * The upload is only part of it: the same call parses every ZIP entry to a disk-backed
     * table and profiles all of their rows, which on full MIMIC-IV overran even an hour.
     * The work is bounded by file size rather than by lock contention - nothing else waits
     * on these rows - so the budget is deliberately far above the observed runtime.
     */
    @Transactional
    @TransactionConfiguration(timeout = 14_400)
    public ConnectorDTO ensureConnector(String name,
                                        FLNetClientConfig.AutoSubscribeGroupConfig groupConfig,
                                        Long cohortId,
                                        String connectorPath,
                                        String keycloakId) {
        ConnectorDTO connector = connectorBO.getAllByCohortIdAndName(cohortId, name)
                .orElseGet(() -> connectorBO.getAllByCohortId(cohortId, keycloakId).stream().findFirst().orElse(null));
        if (connector != null) {
            Log.infof("AutoSubscribe '%s': reusing existing connector %d for cohort %d", name, connector.getId(),
                    cohortId);
            return connector;
        }

        connector = createConnector(name, groupConfig, cohortId, connectorPath, keycloakId);
        if (connector == null) {
            return null;
        }
        Log.infof("AutoSubscribe '%s': using connector %d (%d steps) for cohort %d ", name, connector.getId(),
                connector.getTransformer().size(), cohortId);
        return connector;
    }

    private ConnectorDTO createConnector(String name,
                                         FLNetClientConfig.AutoSubscribeGroupConfig groupConfig,
                                         Long cohortId,
                                         String connectorPath,
                                         String keycloakId) {
        Log.infof("AutoSubscribe '%s': loading connector from %s", name, connectorPath);
        ConnectorDTO connector = connectorBO.importFromRemoteUrl(connectorPath, cohortId, keycloakId, name);
        if (connector == null) {
            Log.warnf("AutoSubscribe '%s': connector import returned no connector", name);
            return null;
        }

        groupConfig.etlDataPath().ifPresent(etlDataPath -> {
            if (etlDataPath.isBlank()) {
                return;
            }
            if (!(connector.getInputConfig() instanceof FileUploadSettingsDTO fileSettings)) {
                Log.warnf("AutoSubscribe '%s': connector input is not a file upload, cannot attach %s",
                        name, etlDataPath);
                return;
            }
            File etlFile = null;
            try {
                etlFile = ResourceLoader.loadAsFile(etlDataPath);
            } catch (IOException e) {
                Log.warnf("AutoSubscribe '%s': failed to load etl-data-path %s: %s", name, etlDataPath, e.getMessage());
                return;
            }
            if (!etlFile.exists()) {
                Log.warnf("AutoSubscribe '%s': etl-data-path %s does not exist", name, etlDataPath);
                return;
            }
            ConnectorFilesDTO uploaded = uploadBO.uploadFileForCohort(
                    cohortId,
                    etlFile,
                    config.user().systemUserName(),
                    fileSettings.toFileParsingSettings()
            );
            fileSettings.setFileId(uploaded.getId());
            connectorBO.update(connector.getId(), connector, keycloakId);
            Log.infof("AutoSubscribe '%s': attached file %d to connector %d",
                    name, uploaded.getId(), connector.getId());
        });

        return connector;
    }
}

package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.core.api.run.RunStatusTypes;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.importer.connector.ConnectorAO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorEntity;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import bio.cosy.feddb.local.api.importer.functions.FunctionExecutionMode;
import bio.cosy.feddb.local.api.importer.functions.FunctionRunnerBO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionOutputAwaiter;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionResult;
import bio.cosy.feddb.local.api.importer.run.execution.ConnectorRunExecutionRunBO;
import bio.cosy.feddb.local.api.importer.run.message.ConnectorRunMessagesAO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepAO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepBO;
import bio.cosy.feddb.local.api.importer.run.step.ConnectorRunStepDTO;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class ConnectorTransformerBO extends BaseBo<
        ConnectorTransformerDTO,
        ConnectorTransformerEntity,
        ConnectorTransformerAO,
        ConnectorTransformerMapper> {

    public static final Duration APP_OUTPUT_TIMEOUT = Duration.ofMinutes(5);

    @Inject
    ConnectorAO connectorAO;

    @Inject
    FunctionRunnerBO functionRunnerBO;

    @Inject
    ConnectorRunExecutionRunBO executionRunBO;

    @Inject
    ConnectorRunExecutionOutputAwaiter outputAwaiter;

    @Inject
    ConnectorRunStepAO runStepAO;

    @Inject
    ConnectorRunMessagesAO runMessagesAO;

    @Inject
    ConnectorRunStepBO stepBO;

    @Inject
    AppTransformerSessionBO sessionBO;

    /**
     * Resolves and attaches mandatory relations and normalizes optional mapping fields.
     */
    protected ConnectorTransformerEntity connectNodes(ConnectorTransformerDTO data, ConnectorTransformerEntity entity) {
        if (data == null) {
            throw new IllegalArgumentException("Transformer payload must not be null");
        }
        if (entity == null) {
            throw new IllegalArgumentException("Transformer entity must not be null");
        }
        if (data.getConnectorId() == null) {
            throw new IllegalArgumentException("connectorId must not be null");
        }

        ConnectorEntity connector = connectorAO.findById(data.getConnectorId());
        if (connector == null) {
            throw new NotFoundException("Connector not found");
        }

        entity.setConnector(connector);

        if (data.getColumn() == null) {
            entity.setColumn("");
        }
        if (data.getInputMapping() == null) {
            entity.setInputMapping(Map.of());
        }
        if (data.getReturnMapping() == null) {
            entity.setReturnMapping(Map.of());
        }

        return entity;
    }

    @Override
    public ConnectorTransformerDTO create(ConnectorTransformerDTO data) {
        if (data == null) {
            throw new IllegalArgumentException("Transformer must not be null");
        }

        boolean isAppTransformer = isAppTransformer(data);

        if (!isAppTransformer && !functionRunnerBO.exists(data.getModuleName(), data.getMethodName())) {
            throw new NotFoundException("Function could not be found");
        }

        ConnectorTransformerEntity entity = mapper.dtoToEntity(data);
        entity = connectNodes(data, entity);
        ConnectorTransformerEntity created = ao.create(entity);
        return mapper.entityToDto(created);
    }

    public void deleteAllForConnector(Long connectorId) {
        ao.deleteAllForConnector(connectorId);
    }

    /**
     * Smart sync of transformers for a connector update.
     * <p>
     * Instead of deleting all transformers and recreating them, this method diffs the incoming
     * list against the existing transformers:
     * <ul>
     *   <li>Unchanged transformers are left untouched</li>
     *   <li>Changed transformers are updated in-place (preserving their ID and FK references from run history)</li>
     *   <li>New transformers (no ID) are created</li>
     *   <li>Removed transformers are safely deleted — if they have run history, FK references in
     *       run steps and messages are nulled out first to preserve the logs</li>
     * </ul>
     */
    public void syncTransformers(Long connectorId, List<ConnectorTransformerDTO> incoming) {
        List<ConnectorTransformerEntity> existing = ao.getAllForConnector(connectorId);
        List<ConnectorTransformerDTO> incomingList = incoming != null ? incoming : Collections.emptyList();

        // Index existing transformers by ID
        Map<Long, ConnectorTransformerEntity> existingById = existing.stream()
                .collect(Collectors.toMap(e -> e.getId(), e -> e));

        // Collect IDs that the client wants to keep
        Set<Long> incomingIds = incomingList.stream()
                .filter(t -> t.getId() != null)
                .map(ConnectorTransformerDTO::getId)
                .collect(Collectors.toSet());

        // 1. Delete transformers that are no longer in the incoming list
        for (ConnectorTransformerEntity existingTransformer : existing) {
            if (!incomingIds.contains(existingTransformer.getId())) {
                safeDelete(existingTransformer);
            }
        }

        // 2. Update existing or create new transformers
        int position = 1;
        for (ConnectorTransformerDTO dto : incomingList) {
            dto.setConnectorId(connectorId);

            if (dto.getId() != null && existingById.containsKey(dto.getId())) {
                // Existing transformer — update only if something changed
                ConnectorTransformerEntity entity = existingById.get(dto.getId());
                updateExisting(entity, dto, position);
            } else {
                // New transformer — create it
                dto.setPosition(position);
                create(dto);
            }
            position++;
        }
    }

    /**
     * Safely deletes a transformer, preserving run history.
     * If the transformer has been referenced by any run step or message,
     * those FK references are nulled out first so the logs survive.
     */
    private void safeDelete(ConnectorTransformerEntity transformer) {
        Long id = transformer.getId();
        int detachedSteps = runStepAO.detachTransformer(id);
        int detachedMessages = runMessagesAO.detachTransformer(id);
        if (detachedSteps > 0 || detachedMessages > 0) {
            Log.infof("Transformer %s had run history — detached %d steps and %d messages before deletion",
                    id, detachedSteps, detachedMessages);
        }
        ao.delete(transformer);
    }

    /**
     * Updates an existing transformer entity in-place if any field has changed.
     * Uses {@link ConnectorTransformerEntity#contentEquals} for a clean business-field comparison.
     * Position is always synced from the incoming list order.
     */
    private void updateExisting(ConnectorTransformerEntity entity, ConnectorTransformerDTO dto, int newPosition) {
        boolean positionChanged = entity.getPosition() == null || entity.getPosition() != newPosition;

        // Map the DTO to a transient entity for content comparison
        ConnectorTransformerEntity incoming = mapper.dtoToEntity(dto);
        boolean contentChanged = !entity.contentEquals(incoming);

        if (positionChanged) {
            entity.setPosition(newPosition);
        }

        if (contentChanged) {
            mapper.updateContentFromDto(dto, entity);
        }

        if (positionChanged || contentChanged) {
            Log.infof("Transformer %s updated in-place (position=%s, content=%s)",
                    entity.getId(),
                    positionChanged ? "changed" : "unchanged",
                    contentChanged ? "changed" : "unchanged"
            );
            ao.persist(entity);
        }
    }

    /**
     * Applies all configured transformers in order. Built-in transformers are executed in-process.
     * App-based transformers are executed through the execution subsystem and must upload their result.
     */
    public List<Map<String, Object>> applyTransformations(
            ConnectorDTO connectorDTO,
            ConnectorRunDTO run,
            List<Map<String, Object>> rows
    ) {
        if (connectorDTO == null) {
            throw new IllegalArgumentException("connectorDTO must not be null");
        }
        if (run == null) {
            throw new IllegalArgumentException("run must not be null");
        }

        List<ConnectorTransformerDTO> transformers = connectorDTO.getTransformer();
        if (transformers == null || transformers.isEmpty()) {
            return rows;
        }

        // Progress is owned by the streaming ETL pipeline (per patient group), so this method
        // only applies the configured transformers in order. The transformer pipeline is invoked
        // once per patient group, which is correct for the row/cell built-in functions.
        int totalSteps = transformers.size();
        for (int i = 0; i < totalSteps; i++) {
            ConnectorTransformerDTO transformer = transformers.get(i);
            run.setCurrentTransformingStep((long) i + 1);
            rows = applyTransformation(transformer, run, rows);
        }

        return rows;
    }

    /**
     * Applies a transformer preview. Remote app transformers are not executed during preview because
     * they require the execution subsystem, container startup and asynchronous output upload.
     */
    public List<Map<String, Object>> applyTransformations(
            ConnectorTransformerDTO transformer,
            List<Map<String, Object>> rows
    ) {
        if (transformer == null) {
            throw new IllegalArgumentException("transformer must not be null");
        }

        if (isAppTransformer(transformer)) {
            Log.infof(
                    "Skipping app-based transformer '%s' during preview; preview only executes built-in functions",
                    transformer.getMethodName()
            );
            return rows;
        }

        return functionRunnerBO.apply(transformer, rows, null, null);
    }

    /**
     * Applies a single built-in transformer respecting its execution mode. PATIENT-mode functions
     * are grouped by the given patient id column and executed per patient (mirroring the ETL
     * pipeline); CELL/ROW functions run over the full row list. App-based transformers require the
     * execution subsystem and are not executed here — the rows are returned unchanged.
     *
     * <p>Shared by the connector preview and the transformed-statistics computation so both use the
     * exact same execution semantics.</p>
     */
    public List<Map<String, Object>> applyBuiltInTransformation(
            ConnectorTransformerDTO transformer,
            List<Map<String, Object>> rows,
            String patientIdColumn) {
        if (transformer == null) {
            throw new IllegalArgumentException("transformer must not be null");
        }
        if (isAppTransformer(transformer)) {
            Log.infof(
                    "Skipping app-based transformer '%s'; it requires the execution subsystem",
                    transformer.getMethodName()
            );
            return rows;
        }
        return functionRunnerBO.mode(transformer) == FunctionExecutionMode.PATIENT
                ? applyPatientTransformation(transformer, rows, patientIdColumn)
                : applyTransformations(transformer, rows);
    }

    /**
     * Groups the given rows by the external patient id column and applies the PATIENT-mode
     * transformer to each patient group, preserving the incoming order of patients.
     */
    public List<Map<String, Object>> applyPatientTransformation(
            ConnectorTransformerDTO transformer,
            List<Map<String, Object>> rows,
            String patientIdColumn) {
        if (patientIdColumn == null || patientIdColumn.isBlank()) {
            throw new BadRequestException(
                    "Patient transformations require a source column mapped to the external patient id");
        }
        Map<String, List<Map<String, Object>>> rowsByPatient = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String patientId = requirePatientId(row, patientIdColumn);
            rowsByPatient.computeIfAbsent(patientId, ignored -> new ArrayList<>()).add(row);
        }
        List<Map<String, Object>> transformed = new ArrayList<>(rows.size());
        for (List<Map<String, Object>> patientRows : rowsByPatient.values()) {
            transformed.addAll(applyTransformations(transformer, patientRows));
        }
        return transformed;
    }

    private String requirePatientId(Map<String, Object> row, String patientIdColumn) {
        if (row == null) {
            throw new BadRequestException("Encountered a null patient row");
        }
        Object patientId = row.get(patientIdColumn);
        if (patientId == null || String.valueOf(patientId).isBlank()) {
            throw new BadRequestException(
                    "Row is missing the external patient id in column '" + patientIdColumn + "'");
        }
        return String.valueOf(patientId);
    }

    /**
     * Applies a single transformer. Built-in transformers are executed directly.
     * App-based transformers are delegated to the execution subsystem and their uploaded
     * tabular output replaces the current row set.
     */
    public List<Map<String, Object>> applyTransformation(
            ConnectorTransformerDTO transformer,
            ConnectorRunDTO run,
            List<Map<String, Object>> rows
    ) {
        if (transformer == null) {
            throw new IllegalArgumentException("transformer must not be null");
        }
        if (run == null) {
            throw new IllegalArgumentException("run must not be null");
        }

        if (isAppTransformer(transformer)) {
            // The container was started once for the whole run; this batch is another run on it.
            // Falling back to a container per batch would make any real import unusable, so a
            // missing session is an error rather than something to paper over.
            return sessionBO.transform(run.getId(), transformer, rows);
        }

        return functionRunnerBO.apply(
                transformer,
                rows,
                run.getCohortId() == null ? null : String.valueOf(run.getCohortId()),
                run.getId()
        );
    }

    /**
     * Executes an app-based transformer via the execution subsystem.
     * <p>
     * Flow:
     * 1. Create a pending step for the current connector run.
     * 2. Start the container through the execution subsystem.
     * 3. Wait reactively until the container uploads its output.
     * 4. Read the uploaded {@link TableData} from the finished step and return its rows.
     * <p>
     * This method is still synchronous from the caller's perspective because the surrounding
     * transformer pipeline expects a materialized row set. Internally, however, waiting for
     * the uploaded result is event-driven and no longer implemented with database polling.
     */
    private List<Map<String, Object>> startAppTransformer(
            ConnectorTransformerDTO transformer,
            ConnectorRunDTO run
    ) {
        Log.infof(
                "Starting app-based transformer '%s' (image: %s) for run %d",
                transformer.getMethodName(),
                transformer.getAppImage(),
                run.getId()
        );

        ConnectorRunStepDTO step = createPendingStep(transformer, run);
        outputAwaiter.register(step.getId(), null);

        try {
            ConnectorRunStepDTO result = executionRunBO.startModel(transformer, step, run.getKeycloakId());
            if (result == null) {
                throw new RuntimeException("Execution subsystem returned no step result");
            }

            if (result.getLastError() != null && !result.getLastError().isBlank()) {
                Log.errorf(
                        "App transformer '%s' failed to start: %s",
                        transformer.getMethodName(),
                        result.getLastError()
                );
                throw new RuntimeException("App transformer failed: " + result.getLastError());
            }

            Log.infof(
                    "App transformer '%s' started with containerId=%s, stepId=%d. Waiting for uploaded output.",
                    transformer.getMethodName(),
                    result.getContainerId(),
                    result.getId()
            );

            ConnectorRunExecutionResult finishedStep = outputAwaiter.awaitOutput(result.getId())
                    .await().atMost(APP_OUTPUT_TIMEOUT);

            if (finishedStep == null) {
                throw new RuntimeException("App transformer finished without a final step");
            }

            TableData outputData = finishedStep.outputData();
            if (outputData == null) {
                throw new RuntimeException("App transformer finished without tabular output");
            }

            try {
                return new ArrayList<>(outputData.getRows());
            } finally {
                outputData.close();
            }

        } catch (RuntimeException e) {
            outputAwaiter.cancel(step.getId());
            stepBO.updateAndNotify(step.getId(), RunStatusTypes.ERROR, e.getMessage());
            throw e;
        } catch (Exception e) {
            Log.errorf(e, "Failed to execute app transformer '%s'", transformer.getMethodName());
            outputAwaiter.cancel(step.getId());
            stepBO.updateAndNotify(step.getId(), RunStatusTypes.ERROR, e.getMessage());
            throw new RuntimeException("Failed to execute app transformer: " + e.getMessage(), e);
        }
    }

    /**
     * Creates the initial pending step that tracks the app transformer execution.
     */
    private ConnectorRunStepDTO createPendingStep(ConnectorTransformerDTO transformer, ConnectorRunDTO run) {
        ConnectorRunStepDTO step = new ConnectorRunStepDTO();
        step.setConnectorRunId(run.getId());
        step.setTransformationId(transformer.getId());
        step.setStatus(RunStatusTypes.PENDING);
        step.setProgress(0f);
        step.setHyperParams(transformer.getHyperparams());
        return stepBO.createInNewTransaction(step);
    }

    private boolean isAppTransformer(ConnectorTransformerDTO transformer) {
        return transformer != null
                && transformer.getAppImage() != null
                && !transformer.getAppImage().isBlank();
    }

}

package bio.cosy.feddb.local.api.importer.load;

import bio.cosy.feddb.core.api.datamodler.schema.SchemaNodeType;
import bio.cosy.feddb.local.api.cohort.CohortBO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.PatientMapper;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryAO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryDTO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.metadata.MetaPatientDataEntryDTO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.metadata.MetaPatientDataEntryEntity;
import bio.cosy.feddb.local.api.importer.connector.BulkImportStatisticsTypeEnum;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.importer.run.patientlog.ConnectorRunPatientLogBO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import io.quarkus.logging.Log;
import jakarta.annotation.Nonnull;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class ConnectorLoadPatientBO {
    @Inject
    CohortBO cohortBO;

    @Inject
    SchemaNodeAO schemaNodeAO;

    @Inject
    PatientDataEntryAO patientDataEntryAO;

    @Inject
    PatientMapper patientDataMapper;

    @Inject
    PatientAO patientAO;

    @Inject
    ConnectorRunPatientLogBO errorLogBO;

    @Inject
    EntityManager entityManager;

    @Transactional
    public void importPatient(
            @Nonnull ConnectorLoadPatient patient,
            @Nonnull ConnectorRunDTO run) {
        Map<Long, SchemaNodeEntity> schemaNodeCache = new HashMap<>();
        boolean newPatient = false;
        try {
            updateStatistics(run, 1, BulkImportStatisticsTypeEnum.RECEIVED_ENTITIES);
            Optional<PatientEntity> existingPatientOptional =
                    Boolean.TRUE.equals(run.getDeleteExistingPatients())
                            ? Optional.empty()
                            : patientAO.findByExternalPatientId(run.getCohortId(), patient.getExternalPatientId());
            PatientEntity patientEntity;
            if (existingPatientOptional.isEmpty()) {
                newPatient = true;
                patientEntity = new PatientEntity();
                patientEntity.setExternalPatientId(patient.getExternalPatientId());
                CohortEntity cohortRef = entityManager.getReference(
                        CohortEntity.class,
                        run.getCohortId()
                );
                patientEntity.setCohort(cohortRef);
                patientAO.persist(patientEntity);
            } else {
                patientEntity = existingPatientOptional.get();
            }

            List<PatientDataEntryEntity> patientDataEntryEntities = new ArrayList<>();
            for (ConnectorLoadRow row : patient.getRows()) {
                String rowGroupId = UUID.randomUUID().toString();
                for (PatientDataEntryDTO patientDataEntry : row.getEntries()) {
                    try {
                        PatientDataEntryEntity dataEntryEntity =
                                mapToPatientDataEntryEntity(patientDataEntry, schemaNodeCache);
                        dataEntryEntity.setPatient(patientEntity);

                        var parentSchemaNode = dataEntryEntity.getSchemaNode().getParent();
                        dataEntryEntity.setImportSchemaGroupId(parentSchemaNode.getId() + ":" + rowGroupId);

                        patientDataEntryEntities
                                .add(dataEntryEntity);
                    } catch (Exception e) {
                        Log.warnf("Error processing data entry for patient %s, schemaNodeId: %s",
                                patient.getExternalPatientId(),
                                patientDataEntry.getSchemaNodeId());
                        errorLogBO.createPatientLog(
                                e.getMessage(),
                                run.getId(),
                                patient.getExternalPatientId(),
                                String.valueOf(patientDataEntry.getSchemaNodeId())
                        );
                        updateStatistics(run, 1, BulkImportStatisticsTypeEnum.FAILED_DATA_ENTRIES);
                    }
                }
            }

            // Eliminate full empty groups
            Map<String, List<PatientDataEntryEntity>> entriesByImportSchemaGroupId =
                    patientDataEntryEntities.stream()
                            .collect(Collectors.groupingBy(PatientDataEntryEntity::getImportSchemaGroupId));
            List<PatientDataEntryEntity> toRemove = new ArrayList<>();
            for (Map.Entry<String, List<PatientDataEntryEntity>> entry : entriesByImportSchemaGroupId.entrySet()) {
                List<PatientDataEntryEntity> importSchemaGroupEntities = entry.getValue();
                boolean allGroupIsEmpty = importSchemaGroupEntities.stream()
                        .allMatch(p -> PatientMapper.getValue(p) == null);
                if (allGroupIsEmpty) {
                    toRemove.addAll(importSchemaGroupEntities);
                }
            }
            patientDataEntryEntities.removeAll(toRemove);

            Map<PatientDataEntrySchemaVisitKey, List<PatientDataEntryEntity>> entriesBySchemaVisit =
                    patientDataEntryEntities.stream()
                            .collect(Collectors.groupingBy(e ->
                                    new PatientDataEntrySchemaVisitKey(
                                            e.getSchemaNode().getId(),
                                            e.getVisitId(),
                                            e.getVisitTimestamp()
                                    )
                            ));

            // Fix ATOMIC_ATTRIBUTEs
            toRemove = new ArrayList<>();
            for (Map.Entry<PatientDataEntrySchemaVisitKey, List<PatientDataEntryEntity>> entry : entriesBySchemaVisit.entrySet()) {
                PatientDataEntrySchemaVisitKey schemaVisitKey = entry.getKey();
                List<PatientDataEntryEntity> schemaVisitGroupEntries = entry.getValue();
                var schemaNode = resolveSchemaNode(schemaVisitKey.schemaNodeId, schemaNodeCache);
                if (schemaNode.getType() == SchemaNodeType.ATOMIC_ATTRIBUTE) {
                    List<PatientDataEntryEntity> nullEntries =
                            schemaVisitGroupEntries.stream()
                                    .filter(e -> PatientMapper.getValue(e) == null)
                                    .toList();
                    if (schemaVisitGroupEntries.size() - nullEntries.size() > 1) {
                        errorLogBO.createPatientLog(String.format(
                                "ATOMIC_ATTRIBUTE schema node (id=%d) has multiple entries in the import file. Only one value is allowed per atomic attribute schema node.",
                                schemaNode.getId()
                        ), run.getId(), patient.getExternalPatientId(), schemaNode.getName());
                        toRemove.addAll(schemaVisitGroupEntries);
                    } else {
                        toRemove.addAll(nullEntries);
                    }
                }
            }
            patientDataEntryEntities.removeAll(toRemove);

            // The atomic-attribute pass may have removed complete schema/visit groups.
            // Rebuild the lookup before deleting old data so rejected input cannot erase
            // an existing value that will not be replaced.
            entriesBySchemaVisit = patientDataEntryEntities.stream()
                    .collect(Collectors.groupingBy(e ->
                            new PatientDataEntrySchemaVisitKey(
                                    e.getSchemaNode().getId(),
                                    e.getVisitId(),
                                    e.getVisitTimestamp()
                            )
                    ));

            // Delete existing data that will be overridden by the import.
            if (existingPatientOptional.isPresent()) {
                for (Map.Entry<PatientDataEntrySchemaVisitKey, List<PatientDataEntryEntity>> entry
                        : entriesBySchemaVisit.entrySet()) {
                    PatientDataEntrySchemaVisitKey key = entry.getKey();
                    try {
                        var schemaNode = resolveSchemaNode(key.schemaNodeId, schemaNodeCache);
                        patientDataEntryAO.deleteBySchemaNode(
                                patientEntity,
                                schemaNode,
                                key.visitId(),
                                key.visitTimestamp()
                        );
                    } catch (Exception e) {
                        Log.errorf(
                                e,
                                "Error removing old data entries for patient %s, schemaNodeId: %s, visitId: %s, visitTimestamp: %s",
                                patient.getExternalPatientId(),
                                key.schemaNodeId(),
                                key.visitId(),
                                key.visitTimestamp()
                        );
                        errorLogBO.createPatientLog(e.getMessage(), run.getId(), patient.getExternalPatientId(), key.schemaNodeId().toString());
                    }
                }
            }

            for (PatientDataEntryEntity patientDataEntryEntity : patientDataEntryEntities) {
                try {
                    patientDataEntryAO.persist(patientDataEntryEntity);
                    updateStatistics(run, 1, BulkImportStatisticsTypeEnum.NEW_DATA_ENTRIES);
                } catch (Exception e) {
                    Log.errorf(
                            e,
                            "Error saving data entry for patient %s, schemaNodeId: %s",
                            patient.getExternalPatientId(),
                            patientDataEntryEntity.getSchemaNode().getId());
                    errorLogBO.createPatientLog(e.getMessage(), run.getId(), patient.getExternalPatientId(), patientDataEntryEntity.getSchemaNode().getName());
                    updateStatistics(run, 1, BulkImportStatisticsTypeEnum.FAILED_DATA_ENTRIES);
                }
            }
            updateStatistics(
                    run,
                    1,
                    newPatient
                            ? BulkImportStatisticsTypeEnum.NEW_ENTITIES
                            : BulkImportStatisticsTypeEnum.UPDATED_ENTITIES
            );
        } catch (Exception e) {
            Log.errorf(e, "Error importing patient with externalPatientId '%s'", patient.getExternalPatientId());
            errorLogBO.createPatientLog(e.getMessage(), run.getId(), patient.getExternalPatientId(), null);
            updateStatistics(run, 1, BulkImportStatisticsTypeEnum.FAILED_ENTITIES);
        } finally {
            updateStatistics(run, 1, BulkImportStatisticsTypeEnum.PROCCESSED_ENTITIES);
        }
    }

    private void updateStatistics(ConnectorRunDTO run, long valueToAdd, BulkImportStatisticsTypeEnum type) {
        addToStatistic(run, valueToAdd, type);
    }

    private static void addToStatistic(ConnectorRunDTO run, long valueToAdd, BulkImportStatisticsTypeEnum type) {
        switch (type) {
            case NEW_ENTITIES:
                run.setNewEntities(Optional.ofNullable(run.getNewEntities()).orElseGet(() -> 0L) + valueToAdd);
                break;
            case UPDATED_ENTITIES:
                run.setUpdatedEntities(Optional.ofNullable(run.getUpdatedEntities()).orElseGet(() -> 0L) + valueToAdd);
                break;
            case DELETED_ENTITIES:
                run.setDeletedEntities(Optional.ofNullable(run.getDeletedEntities()).orElseGet(() -> 0L) + valueToAdd);
                break;
            case FAILED_ENTITIES:
                run.setFailedEntities(Optional.ofNullable(run.getFailedEntities()).orElseGet(() -> 0L) + valueToAdd);
                break;
            case UNCHANGED_ENTITIES:
                run.setUnchangedEntities(
                        Optional.ofNullable(run.getUnchangedEntities()).orElseGet(() -> 0L) + valueToAdd);
                break;
            case RECEIVED_ENTITIES:
                run.setReceivedEntities(
                        Optional.ofNullable(run.getReceivedEntities()).orElseGet(() -> 0L) + valueToAdd);
                break;
            case PROCCESSED_ENTITIES:
                run.setProcessedEntities(
                        Optional.ofNullable(run.getProcessedEntities()).orElseGet(() -> 0L) + valueToAdd);
                break;
            case NEW_DATA_ENTRIES:
                run.setNewDataEntries(Optional.ofNullable(run.getNewDataEntries()).orElseGet(() -> 0L) + valueToAdd);
                break;
            case FAILED_DATA_ENTRIES:
                run.setFailedDataEntries(
                        Optional.ofNullable(run.getFailedDataEntries()).orElseGet(() -> 0L) + valueToAdd);
                break;
        }
    }

    private PatientDataEntryEntity mapToPatientDataEntryEntity(
            @Nonnull PatientDataEntryDTO dto,
            Map<Long, SchemaNodeEntity> schemaNodeCache) {

        SchemaNodeEntity schemaNode = resolveSchemaNode(dto.getSchemaNodeId(), schemaNodeCache);
        if (schemaNode.getType() == SchemaNodeType.GROUP || schemaNode.getType() == SchemaNodeType.ROOT) {
            throw new IllegalArgumentException("Schema node with ID " + schemaNode.getId() +
                    " is not an attribute node (type: " + schemaNode.getType() + ")");
        }

        PatientDataEntryEntity entity = new PatientDataEntryEntity();
        entity.setSchemaNode(schemaNode);
        entity.setVisitId(dto.getVisitId());
        entity.setVisitTimestamp(dto.getVisitTimestamp() == null ? null
                : patientDataMapper.normalizeVisitTimestamp(dto.getVisitTimestamp(), dto.getVisitTimestampFormat()));
        entity.setVisitTimestampFormat(dto.getVisitTimestampFormat());
        patientDataMapper.setValue(entity, dto.getValue());

        if (dto.getMetaData() != null) {
            for (MetaPatientDataEntryDTO metaDTO : dto.getMetaData()) {
                MetaPatientDataEntryEntity metaEntity = mapToMetaPatientDataEntryEntity(metaDTO, schemaNodeCache);
                metaEntity.setPatientDataEntry(entity);
                entity.getMetaDataEntries().add(metaEntity);
            }
        }

        return entity;
    }

    private MetaPatientDataEntryEntity mapToMetaPatientDataEntryEntity(
            @Nonnull MetaPatientDataEntryDTO dto,
            Map<Long, SchemaNodeEntity> schemaNodeCache) {
        MetaPatientDataEntryEntity entity = new MetaPatientDataEntryEntity();
        entity.setSchemaNode(resolveSchemaNode(dto.getSchemaNodeId(), schemaNodeCache));
        patientDataMapper.setValue(entity, dto.getValue());
        return entity;
    }

    private SchemaNodeEntity resolveSchemaNode(Long schemaNodeId, Map<Long, SchemaNodeEntity> schemaNodeCache) {
        SchemaNodeEntity schemaNode = schemaNodeCache.get(schemaNodeId);
        if (schemaNode == null) {
            schemaNode = schemaNodeAO.findByIdWithParent(schemaNodeId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Schema node with ID " + schemaNodeId + " does not exist."
                    ));
            schemaNodeCache.put(schemaNodeId, schemaNode);
        }
        return schemaNode;
    }

    private record PatientDataEntrySchemaVisitKey(
            Long schemaNodeId,
            String visitId,
            Instant visitTimestamp
    ) {
    }
}

package bio.cosy.feddb.local.api.cohort.patient.export;

import bio.cosy.feddb.core.api.project.PatientDataExportConfigDTO;
import bio.cosy.feddb.core.api.project.PatientExportFeatureDTO;
import bio.cosy.feddb.core.api.project.PatientExportFilterDTO;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.SelectedDataIdsDTO;
import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.cohort.patient.PatientEntity;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryBO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryExportDTO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunBO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunDTO;
import bio.cosy.feddb.local.api.cohort.patient.tools.PatientToolRunType;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectBO;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

import java.nio.file.Path;
import java.util.*;

@ApplicationScoped
public class PatientDataExportBO {

    @Inject
    PatientAO patientAO;

    @Inject
    FederatedLearningProjectBO projectBO;

    @Inject
    PatientDataEntryBO patientDataEntryBO;

    @Inject
    PatientDataExportTransformerBO transformerBO;

    @Inject
    PatientToolRunBO patientToolRunBO;

    @Transactional
    public List<Map<String, Object>> exportPatientData(Long cohortId, PatientDataExportConfigDTO config) {
        List<PatientDataEntryExportDTO> dataEntries = applyFeatureSelection(getDataForCohort(cohortId, config), config);
        return formatConfiguredDataEntries(dataEntries, config);
    }

    public List<Path> exportPatientDataTool(Long cohortId, PatientDataExportConfigDTO config) {
        Path input = writeAppExportInput(cohortId, config);
        return patientToolRunBO.runAndAwait(cohortId, config.getGlobalAppVersionId(), config.getHyperParams(),
                PatientToolRunType.EXPORT, input);
    }

    @ActivateRequestContext
    public PatientToolRunDTO startAppExport(Long cohortId, PatientDataExportConfigDTO config) {
        Path input = writeAppExportInput(cohortId, config);
        return patientToolRunBO.start(cohortId, config.getGlobalAppVersionId(), config.getHyperParams(),
                PatientToolRunType.EXPORT, input);
    }

    public void requireAppExportConfig(PatientDataExportConfigDTO config) {
        if (config == null || !config.isAppBased() || config.getGlobalAppVersionId() == null) {
            throw new BadRequestException("An app-based export needs an app version");
        }
    }

    private Path writeAppExportInput(Long cohortId, PatientDataExportConfigDTO config) {
        requireAppExportConfig(config);
        List<PatientDataEntryExportDTO> dataEntries = QuarkusTransaction.requiringNew().call(() ->
                applyFeatureSelection(getDataForCohort(cohortId, config), config));
        if (dataEntries.isEmpty()) {
            throw new BadRequestException("The cohort has no patient data to export");
        }
        return PatientDataExportHelper.writeStreamToTempFile(dataEntries);
    }

    private List<Path> runAppBasedExport(List<PatientDataEntryExportDTO> dataEntries, PatientDataExportConfigDTO config) {
        Path input = PatientDataExportHelper.writeStreamToTempFile(dataEntries);
        return patientToolRunBO.runAndAwait(null, config.getGlobalAppVersionId(), config.getHyperParams(),
                PatientToolRunType.EXPORT, input);
    }

    public Path exportDataForLearning(PatientDataExportConfigDTO config, Long queryId, Long learningRequestId) {
        if (queryId == null) {
            throw new IllegalArgumentException("Project does not have a query defined");
        }
        List<SelectedDataIdsDTO> selectedDataIds = resolveSelectedDataIds(config);
        Log.debugf("Selected data IDs for request " + learningRequestId + ": " + selectedDataIds);
        Log.infof("Selected data for request " + learningRequestId + " - " + selectedDataIds.size());

        if (selectedDataIds.isEmpty()) {
            throw new IllegalArgumentException("Project does not have selected data IDs defined");
        }
        List<PatientDataEntryExportDTO> dataEntries = applyFeatureSelection(
                getDataForProject(learningRequestId, selectedDataIds),
                config
        );
        Log.info("Found " + dataEntries.size() + " data entries for request " + learningRequestId);
        if (dataEntries.isEmpty()) {
            throw new IllegalArgumentException("No data entries found for the given query and selected data IDs");
        }
        if (config != null && config.isAppBased()) {
            return runAppBasedExport(dataEntries, config).stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("App based export did not produce an output file"));
        }
        return PatientDataExportHelper.writeExportedRowsToTempFile(formatConfiguredDataEntries(dataEntries, config));
    }

    public List<Map<String, Object>> exportDataForPatient(Long cohortId, Long patientId, PatientDataExportConfigDTO config) {

        List<PatientDataEntryExportDTO> dataEntries = applyFeatureSelection(
                patientDataEntryBO.findByCohortAndPatient(cohortId, patientId),
                config
        );
        return formatConfiguredDataEntries(dataEntries, config);
    }

    public List<Map<String, Object>> exportDataForProject(Long cohortId, Long projectId, PatientDataExportConfigDTO config) {

        List<PatientDataEntryExportDTO> dataEntries = applyFeatureSelection(getDataForProject(cohortId, projectId), config);
        return formatConfiguredDataEntries(dataEntries, config);
    }

    public List<PatientDataEntryExportDTO> getDataForCohort(Long cohortId, PatientDataExportConfigDTO config) {
        List<Long> patientIds = resolveCohortPatientIds(cohortId, config);
        Log.info("Found " + patientIds.size() + " patients for cohortId ID: " + cohortId);
        return patientDataEntryBO.findByIds(patientIds);
    }


    private List<Long> resolveCohortPatientIds(Long cohortId, PatientDataExportConfigDTO config) {
        PatientExportFilterDTO filter = config != null ? config.getPatientFilter() : null;
        if (filter == null) {
            return patientAO.findIdsByCohortId(cohortId);
        }

        List<String> externalPatientIds = filter.getExternalPatientIds() == null ? null
                : filter.getExternalPatientIds().stream()
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(id -> !id.isEmpty())
                        .distinct()
                        .toList();
        if (externalPatientIds != null && externalPatientIds.isEmpty()) {
            externalPatientIds = null;
        }

        return patientAO.findIdsByCohortId(cohortId, externalPatientIds, filter.getLimit());
    }

    public List<PatientDataEntryExportDTO> getDataForProject(Long learningRequestId, List<SelectedDataIdsDTO> selectedDataIds) {
        List<PatientEntity> patients = patientAO.findAllByLearningRequestId(learningRequestId);
        List<Long> patientIds = patients.stream()
                .map(PatientEntity::getId)
                .toList();
        Log.info("Found " + patientIds.size() + " patients for learning request ID: " + learningRequestId);
        return patientDataEntryBO.findByIdsAndDataIds(patientIds, selectedDataIds);
    }

    public List<PatientDataEntryExportDTO> getDataForProject(Long cohortId, Long projectId) {
        ProjectDetailDTO project = projectBO.getById(projectId);
        Long queryId = project.getQueryId();
        List<Long> patientIds = patientAO.findEffectedPatientsByQueryId(queryId).stream()
                .map(PatientEntity::getId)
                .toList();
        return patientDataEntryBO.findByCohortAndPatientIds(cohortId, patientIds);
    }

    private List<Map<String, Object>> formatConfiguredDataEntries(
            List<PatientDataEntryExportDTO> dataEntries,
            PatientDataExportConfigDTO config
    ) {
        return transformerBO.formatDataEntries(
                dataEntries,
                config != null && config.isWideFormat(),
                config != null ? config.getJoinFields() : null,
                config != null ? config.getDuplicatePolicy() : null,
                getOrderedFeatureNames(config)
        );
    }

    private List<PatientDataEntryExportDTO> applyFeatureSelection(
            List<PatientDataEntryExportDTO> dataEntries,
            PatientDataExportConfigDTO config
    ) {
        if (config == null || config.getFeatures() == null || config.getFeatures().isEmpty()) {
            return dataEntries;
        }

        Map<String, String> featureNamesByKey = new LinkedHashMap<>();
        for (PatientExportFeatureDTO feature : getOrderedFeatures(config)) {
            String featureName = resolveFeatureName(feature);
            for (SelectedDataIdsDTO selectedDataId : resolveSelectedDataIds(feature)) {
                featureNamesByKey.put(selectedDataId.toUniqueKey(), featureName);
            }
        }

        return dataEntries.stream()
                .filter(Objects::nonNull)
                .filter(entry -> featureNamesByKey.containsKey(toEntryKey(entry)))
                .peek(entry -> entry.setName(featureNamesByKey.get(toEntryKey(entry))))
                .toList();
    }

    private List<SelectedDataIdsDTO> resolveSelectedDataIds(PatientDataExportConfigDTO config) {
        if (config == null || config.getFeatures() == null || config.getFeatures().isEmpty()) {
            return List.of();
        }

        return getOrderedFeatures(config).stream()
                .flatMap(feature -> resolveSelectedDataIds(feature).stream())
                .distinct()
                .toList();
    }

    private List<SelectedDataIdsDTO> resolveSelectedDataIds(PatientExportFeatureDTO feature) {
        if (feature == null || feature.getAllowedDataIds() == null || feature.getAllowedDataIds().isEmpty()) {
            throw new IllegalArgumentException("Project does not have selected data IDs defined");
        }

        List<SelectedDataIdsDTO> selectedDataIds = feature.getAllowedDataIds().stream()
                .filter(Objects::nonNull)
                .filter(dataId -> dataId.getGlobalOntologyId() != null)
                .filter(dataId -> dataId.getGlobalDataTypeId() != null)
                .distinct()
                .toList();

        if (selectedDataIds.isEmpty()) {
            throw new IllegalArgumentException("Project does not have selected data IDs defined");
        }
        return selectedDataIds;
    }

    private List<String> getOrderedFeatureNames(PatientDataExportConfigDTO config) {
        if (config == null || config.getFeatures() == null || config.getFeatures().isEmpty()) {
            return List.of();
        }

        Set<String> featureNames = new LinkedHashSet<>();
        for (PatientExportFeatureDTO feature : getOrderedFeatures(config)) {
            String featureName = resolveFeatureName(feature);
            if (!featureNames.add(featureName)) {
                throw new IllegalArgumentException("Duplicate export feature name: " + featureName);
            }
        }
        return new ArrayList<>(featureNames);
    }

    private List<PatientExportFeatureDTO> getOrderedFeatures(PatientDataExportConfigDTO config) {
        return config.getFeatures().stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(
                        feature -> Optional.ofNullable(feature.getOrder()).orElse(Integer.MAX_VALUE)
                ))
                .toList();
    }

    private String resolveFeatureName(PatientExportFeatureDTO feature) {
        if (feature.getName() != null && !feature.getName().isBlank()) {
            return feature.getName().trim();
        }
        if (feature.getTargetDatatypeId() != null && !feature.getTargetDatatypeId().isBlank()) {
            return feature.getTargetDatatypeId();
        }

        return resolveSelectedDataIds(feature).stream()
                .findFirst()
                .map(SelectedDataIdsDTO::getGlobalDataTypeId)
                .orElseThrow(() -> new IllegalArgumentException("Project does not have selected data IDs defined"));
    }

    private String toEntryKey(PatientDataEntryExportDTO entry) {
        return entry.getOntologyId() + "@" + entry.getDatatypeId();
    }

}

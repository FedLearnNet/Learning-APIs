package bio.cosy.feddb.local.api.importer.validation;

import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorValueMappingConfigDTO;
import bio.cosy.feddb.local.api.importer.files.table.SheetMergePlan;
import bio.cosy.feddb.local.api.importer.statistics.TransformedStatisticsBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@ApplicationScoped
public class PreviewValidationBO {

    @Inject
    TransformedStatisticsBO transformedStatisticsBO;

    @Inject
    ConnectorValidationBO validationBO;

    /** Upper bound on distinct categorical values validated per column, to bound validation cost. */
    @ConfigProperty(name = "connector.preview-validation.max-categorical-values", defaultValue = "50")
    int maxCategoricalValues;

    public Multi<PreviewValidationResponseDTO> validate(PreviewValidationRequestDTO request) {
        return Multi.createFrom().deferred(() -> validationStream(request));
    }

    private Multi<PreviewValidationResponseDTO> validationStream(PreviewValidationRequestDTO request) {
        Map<String, ConnectorMappingDTO> mappingByColumn = mappingByColumn(request.getSchemaMapping());
        Map<String, PreviewValidationRequestElementDTO> elementByMapping =
                elementByMapping(request.getElements());
        ColumnWarningContext warningContext = buildWarningContext(
                request.getSchemaMapping(), request.getTransformer());

        TransformationImpact impact = transformationImpact(request);
        List<ColumnProfile> sourceProfiles = impact.structuralChange()
                ? List.of()
                : transformedStatisticsBO.getSourceStatistics(
                        request.getCohortId(), request.getInputConfig(), request.getUploadInfo());
        List<ColumnProfile> fastProfiles = impact.allColumns()
                ? List.of()
                : sourceProfiles.stream()
                        .filter(Objects::nonNull)
                        .filter(profile -> !impact.columns().contains(profile.name()))
                        .toList();
        Set<String> fastColumns = fastProfiles.stream()
                .map(ColumnProfile::name)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        Multi<PreviewValidationResponseDTO> pending = Multi.createFrom().iterable(
                        initialColumns(request, sourceProfiles))
                .map(this::pendingResponse);
        Multi<PreviewValidationResponseDTO> fastResults = Multi.createFrom().iterable(fastProfiles)
                .map(profile -> validateProfile(
                        request.getCohortId(),
                        profile,
                        mappingByColumn.get(profile.name()),
                        elementByMapping,
                        warningContext
                ));

        boolean needsTransformedProfiles = impact.structuralChange()
                || impact.hasTransformers()
                || sourceProfiles.isEmpty();
        Multi<PreviewValidationResponseDTO> transformedResults = needsTransformedProfiles
                ? Multi.createFrom().item(() -> transformedStatisticsBO.getTransformedStatistics(
                                request.getCohortId(),
                                request.getInputConfig(),
                                request.getUploadInfo(),
                                request.getMergeConfig(),
                                request.getPivotConfig(),
                                request.getTransformer(),
                                request.getSchemaMapping()
                        ))
                        .onItem().transformToMultiAndConcatenate(profiles -> Multi.createFrom().iterable(
                                profiles == null ? List.<ColumnProfile>of() : profiles))
                        .filter(Objects::nonNull)
                        .filter(profile -> !fastColumns.contains(profile.name()))
                        .map(profile -> validateProfile(
                                request.getCohortId(),
                                profile,
                                mappingByColumn.get(profile.name()),
                                elementByMapping,
                                warningContext
                        ))
                : Multi.createFrom().empty();

        return Multi.createBy().concatenating().streams(
                pending,
                fastResults,
                transformedResults
        );
    }

    private PreviewValidationResponseDTO pendingResponse(String column) {
        PreviewValidationResponseDTO response = new PreviewValidationResponseDTO();
        response.setColumn(column);
        response.setChecks(List.of());
        return response;
    }

    private List<String> initialColumns(
            PreviewValidationRequestDTO request,
            List<ColumnProfile> sourceProfiles
    ) {
        LinkedHashSet<String> columns = new LinkedHashSet<>();
        if (sourceProfiles != null) {
            sourceProfiles.stream()
                    .filter(Objects::nonNull)
                    .map(ColumnProfile::name)
                    .filter(this::hasText)
                    .forEach(columns::add);
        }
        if (request.getSchemaMapping() != null) {
            for (ConnectorMappingDTO mapping : request.getSchemaMapping()) {
                if (mapping == null) {
                    continue;
                }
                addColumn(columns, mapping.getColumn());
                if (mapping.getValueMappingConfig() != null) {
                    addColumn(columns, mapping.getValueMappingConfig().getMappingColumn());
                    addColumn(columns, mapping.getValueMappingConfig().getValueColumn());
                }
            }
        }
        if (request.getTransformer() != null) {
            request.getTransformer().stream()
                    .filter(Objects::nonNull)
                    .flatMap(transformer -> transformerOutputColumns(transformer).stream())
                    .forEach(columns::add);
        }
        return List.copyOf(columns);
    }

    private TransformationImpact transformationImpact(PreviewValidationRequestDTO request) {
        boolean structuralChange = SheetMergePlan.requested(request.getMergeConfig())
                || request.getPivotConfig() != null
                && request.getPivotConfig().getValueColumnIndex() != null
                && !request.getPivotConfig().getValueColumnIndex().isEmpty();
        Set<String> columns = new LinkedHashSet<>();
        boolean allColumns = structuralChange;
        boolean hasTransformers = false;
        if (request.getTransformer() != null) {
            for (ConnectorTransformerDTO transformer : request.getTransformer()) {
                if (transformer == null) {
                    continue;
                }
                hasTransformers = true;
                if (transformer.getAppImage() != null && !transformer.getAppImage().isBlank()) {
                    allColumns = true;
                }
                columns.addAll(transformerOutputColumns(transformer));
            }
        }
        return new TransformationImpact(
                structuralChange,
                allColumns,
                hasTransformers,
                Set.copyOf(columns)
        );
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private void addColumn(Set<String> columns, String column) {
        if (hasText(column)) {
            columns.add(column.trim());
        }
    }

    private PreviewValidationResponseDTO validateProfile(
            Long cohortId,
            ColumnProfile profile,
            ConnectorMappingDTO mapping,
            Map<String, PreviewValidationRequestElementDTO> elementByMapping,
            ColumnWarningContext warningContext
    ) {
        PreviewValidationResponseDTO response = new PreviewValidationResponseDTO();
        response.setColumn(profile.name());
        response.setChecks(values(profile).stream()
                .map(value -> validateValue(cohortId, value, mapping, elementByMapping))
                .toList());
        response.setWarnings(warningContext.warningsFor(profile.name()));
        return response;
    }

    private PreviewValidationResponseElementDTO validateValue(
            Long cohortId,
            String value,
            ConnectorMappingDTO mapping,
            Map<String, PreviewValidationRequestElementDTO> elementByMapping
    ) {
        PreviewValidationResponseElementDTO check = new PreviewValidationResponseElementDTO();
        check.setValue(value);
        check.setMapped(mapping != null);

        if (mapping == null) {
            check.setValidated(false);
            check.setResult(null);
            return check;
        }

        PreviewValidationRequestElementDTO element = elementByMapping.get(mapping.getMapping());
        Long schemaId = mapping.getSchemaId() != null
                ? mapping.getSchemaId()
                : element == null ? null : element.getSchemaId();
        ConnectorValidationResultDTO result = validationBO.checkValidations(
                cohortId,
                value,
                schemaId,
                mapping.getMapping(),
                null
        );
        check.setValidated(true);
        check.setResult(result);
        return check;
    }

    private Map<String, ConnectorMappingDTO> mappingByColumn(List<ConnectorMappingDTO> schemaMapping) {
        Map<String, ConnectorMappingDTO> result = new LinkedHashMap<>();
        if (schemaMapping != null) {
            for (ConnectorMappingDTO mapping : schemaMapping) {
                if (mapping != null && mapping.getColumn() != null && !mapping.getColumn().isBlank()) {
                    result.putIfAbsent(mapping.getColumn(), mapping);
                }
            }
        }
        return result;
    }

    private Map<String, PreviewValidationRequestElementDTO> elementByMapping(
            List<PreviewValidationRequestElementDTO> elements
    ) {
        Map<String, PreviewValidationRequestElementDTO> result = new LinkedHashMap<>();
        if (elements != null) {
            for (PreviewValidationRequestElementDTO element : elements) {
                if (element != null && element.getMapping() != null && !element.getMapping().isBlank()) {
                    result.putIfAbsent(element.getMapping(), element);
                }
            }
        }
        return result;
    }

    private List<String> values(ColumnProfile profile) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        if (isNumeric(profile)) {
            // For numeric columns testing the representative boundaries (min, mean, max)
            // is enough - iterating over every distinct value adds no validation coverage.
            addNumericValue(values, profile.type(), profile.min());
            addNumericValue(values, profile.type(), profile.mean());
            addNumericValue(values, profile.type(), profile.max());
        } else if (profile.valueCounts() != null) {
            addCategoricalValues(values, profile.valueCounts());
        } else if (profile.topCategories() != null) {
            addCategoricalValues(values, profile.topCategories());
        }
        // Missingness is not a category, but it is a separate validation case: the selected schema
        // target may be required or may explicitly disallow null values.
        if (profile.missing() > 0) {
            values.add("");
        }
        return new ArrayList<>(values);
    }

    private void addCategoricalValues(
            LinkedHashSet<String> values, List<Map.Entry<String, Integer>> distinctValues) {
        // Cap high-cardinality columns: value counts are ordered by frequency, so the first entries
        // are the most representative sample. Blank/empty values are added once from the profile's
        // missing counter below instead of being treated as a category.
        distinctValues.stream()
                .filter(Objects::nonNull)
                .map(Map.Entry::getKey)
                .filter(key -> key != null && !key.isBlank())
                .limit(maxCategoricalValues)
                .forEach(values::add);
    }

    private boolean isNumeric(ColumnProfile profile) {
        String type = profile.type();
        return "INTEGER".equalsIgnoreCase(type) || "NUMBER".equalsIgnoreCase(type);
    }

    private void addNumericValue(LinkedHashSet<String> values, String type, Double value) {
        if (value == null) {
            return;
        }
        if ("INTEGER".equalsIgnoreCase(type)) {
            values.add(Long.toString(Math.round(value)));
        } else {
            values.add(Double.toString(value));
        }
    }

    /**
     * Precomputes, from the mapping and transformer config, which columns should raise warnings and
     * why. See {@link PreviewValidationWarningType}.
     */
    private ColumnWarningContext buildWarningContext(
            List<ConnectorMappingDTO> schemaMapping, List<ConnectorTransformerDTO> transformers) {
        Set<String> transformerOutputs = new HashSet<>();
        Set<String> appTransformerOutputs = new HashSet<>();
        if (transformers != null) {
            for (ConnectorTransformerDTO transformer : transformers) {
                if (transformer == null) {
                    continue;
                }
                Set<String> outputs = transformerOutputColumns(transformer);
                transformerOutputs.addAll(outputs);
                if (transformer.getAppImage() != null && !transformer.getAppImage().isBlank()) {
                    appTransformerOutputs.addAll(outputs);
                }
            }
        }

        Map<String, Integer> referenceCount = new HashMap<>();
        if (schemaMapping != null) {
            for (ConnectorMappingDTO mapping : schemaMapping) {
                if (mapping == null) {
                    continue;
                }
                countReference(referenceCount, mapping.getColumn());
                ConnectorValueMappingConfigDTO valueMapping = mapping.getValueMappingConfig();
                if (valueMapping != null) {
                    countReference(referenceCount, valueMapping.getMappingColumn());
                    countReference(referenceCount, valueMapping.getValueColumn());
                }
            }
        }

        return new ColumnWarningContext(transformerOutputs, appTransformerOutputs, referenceCount);
    }

    private Set<String> transformerOutputColumns(ConnectorTransformerDTO transformer) {
        Set<String> columns = new HashSet<>();
        if (transformer.getColumn() != null) {
            for (String column : transformer.getColumn().split(",")) {
                if (!column.isBlank()) {
                    columns.add(column.trim());
                }
            }
        }
        if (transformer.getReturnMapping() != null) {
            for (Object value : transformer.getReturnMapping().values()) {
                if (value != null && !String.valueOf(value).isBlank()) {
                    columns.add(String.valueOf(value).trim());
                }
            }
        }
        return columns;
    }

    private record TransformationImpact(
            boolean structuralChange,
            boolean allColumns,
            boolean hasTransformers,
            Set<String> columns
    ) {
    }

    private void countReference(Map<String, Integer> referenceCount, String column) {
        if (column != null && !column.isBlank()) {
            referenceCount.merge(column, 1, Integer::sum);
        }
    }

    /** Holds the precomputed warning inputs and derives per-column warnings on demand. */
    private record ColumnWarningContext(
            Set<String> transformerOutputs,
            Set<String> appTransformerOutputs,
            Map<String, Integer> referenceCount
    ) {
        List<PreviewValidationWarningDTO> warningsFor(String column) {
            List<PreviewValidationWarningDTO> warnings = new ArrayList<>();
            boolean referenced = referenceCount.getOrDefault(column, 0) > 0;

            if (appTransformerOutputs.contains(column)) {
                warnings.add(new PreviewValidationWarningDTO(
                        PreviewValidationWarningType.APP_TRANSFORMER_OUTPUT,
                        "Column '" + column + "' is produced by an app-based transformer, so its "
                                + "categories cannot be computed here. Add expected values manually."));
            }
            if (referenced && transformerOutputs.contains(column)) {
                warnings.add(new PreviewValidationWarningDTO(
                        PreviewValidationWarningType.TRANSFORMED_MAPPING_KEY,
                        "Column '" + column + "' is created or overwritten by a transformer and also "
                                + "used as a mapping key. Its categories may be unreliable."));
            }
            if (referenceCount.getOrDefault(column, 0) > 1) {
                warnings.add(new PreviewValidationWarningDTO(
                        PreviewValidationWarningType.MULTIPLE_MAPPING_KEYS,
                        "Column '" + column + "' is referenced by more than one mapping key."));
            }
            return warnings.isEmpty() ? null : warnings;
        }
    }
}

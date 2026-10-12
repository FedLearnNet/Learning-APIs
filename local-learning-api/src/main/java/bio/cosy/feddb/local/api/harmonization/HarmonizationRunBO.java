package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDetailDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import bio.cosy.feddb.local.api.importer.run.preview.AppTransformerPreviewBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformationBO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntityMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;


@ApplicationScoped
public class HarmonizationRunBO {

    private static final int PREVIEW_ROWS = 20;

    @Inject
    ConnectorTransformationBO transformationBO;

    @Inject
    AppTransformerPreviewBO appPreviewBO;

    @Inject
    ConnectorValidationBO validationBO;

    @Inject
    DataTypeEntityMapper dataTypeMapper;

    @Inject
    HarmonizationFileBO fileBO;


    public HarmonizationResultDTO run(HarmonizationDTO harmonization, String keycloakId) {
        List<Map<String, Object>> reference = fileBO.rows(harmonization.getReferenceFileId(), null);
        List<Map<String, Object>> source = fileBO.rows(harmonization.getSourceFileId(), harmonization.getSourceMergeConfig());
        HarmonizationResultDTO harmonized = harmonize(harmonization, reference,
                transform(harmonization, harmonization.getTransformer(), source, keycloakId));
        List<Map<String, Object>> rows = new ArrayList<>(reference);
        rows.addAll(harmonized.getPreview());
        ConnectorFilesDetailDTO file = fileBO.save(rows, harmonization.getName() + ".csv", keycloakId);
        return new HarmonizationResultDTO(file.getId(), rows.size(), harmonized.getRejected(), harmonized.getUnmatched(),
                harmonized.getPreview().subList(0, Math.min(PREVIEW_ROWS, harmonized.getPreview().size())));
    }

    public List<Map<String, Object>> previewTransformed(HarmonizationDTO harmonization, int steps, String keycloakId) {
        List<ConnectorTransformerDTO> transformers = harmonization.getTransformer();
        return transform(harmonization, transformers.subList(0, Math.max(0, Math.min(steps, transformers.size()))),
                sourcePreview(harmonization, keycloakId), keycloakId);
    }

    public HarmonizationResultDTO previewHarmonized(HarmonizationDTO harmonization, String keycloakId) {
        fileBO.getById(harmonization.getReferenceFileId(), keycloakId);
        return harmonize(harmonization, fileBO.rows(harmonization.getReferenceFileId(), null), transform(harmonization,
                harmonization.getTransformer(), sourcePreview(harmonization, keycloakId), keycloakId));
    }


    private HarmonizationResultDTO harmonize(HarmonizationDTO harmonization, List<Map<String, Object>> referenceRows,
                                             List<Map<String, Object>> sourceRows) {
        Map<String, Map<String, Object>> referenceByKey = byKey(referenceRows, harmonization.getReferenceKey());
        Map<String, String> sourceColumns = sourceColumnByTarget(harmonization);
        List<DataTypeNodeDTO> dataTypes = harmonization.getTargets().stream()
                .map(target -> dataTypeMapper.dtoToNodeDto(target.getDataType()))
                .toList();
        Map<String, Integer> rejected = new LinkedHashMap<>();
        Set<String> unmatched = new LinkedHashSet<>();
        List<Map<String, Object>> harmonized = new ArrayList<>();
        for (Map<String, Object> row : sourceRows) {
            String key = String.valueOf(row.get(harmonization.getSourceKey()));
            Map<String, Object> referenceRow = referenceByKey.get(key);
            if (referenceRow == null) {
                unmatched.add(key);
            }
            harmonized.add(harmonizeRow(harmonization, row, referenceRow, sourceColumns, dataTypes, rejected));
        }
        return new HarmonizationResultDTO(null, harmonized.size(), rejected, List.copyOf(unmatched), harmonized);
    }

    private Map<String, Object> harmonizeRow(HarmonizationDTO harmonization, Map<String, Object> row,
                                             Map<String, Object> referenceRow, Map<String, String> sourceColumns,
                                             List<DataTypeNodeDTO> dataTypes, Map<String, Integer> rejected) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(harmonization.getReferenceKey(), row.get(harmonization.getSourceKey()));
        List<HarmonizationTargetDTO> targets = harmonization.getTargets();
        for (int index = 0; index < targets.size(); index++) {
            HarmonizationTargetDTO target = targets.get(index);
            if (referenceRow != null && target.isStaticColumn()) {
                result.put(target.getName(), referenceRow.get(target.getName()));
            } else {
                String sourceColumn = sourceColumns.get(target.getName());
                Object value = sourceColumn == null ? null : row.get(sourceColumn);
                result.put(target.getName(), validValue(value, index, target.getName(), dataTypes.get(index), rejected));
            }
        }
        return result;
    }

    private Object validValue(Object value, int targetIndex, String target, DataTypeNodeDTO dataType,
                              Map<String, Integer> rejected) {
        if (validationBO.rejection(value, HarmonizationConnectorMapper.targetId(targetIndex), dataType) == null) {
            return value;
        }
        rejected.merge(target, 1, Integer::sum);
        return null;
    }

    private Map<String, Map<String, Object>> byKey(List<Map<String, Object>> rows, String key) {
        Map<String, Map<String, Object>> byKey = new HashMap<>();
        rows.forEach(row -> byKey.putIfAbsent(String.valueOf(row.get(key)), row));
        return byKey;
    }

    private Map<String, String> sourceColumnByTarget(HarmonizationDTO harmonization) {
        return harmonization.getMapping().stream()
                .filter(mapping -> mapping.getColumn() != null && mapping.getMapping() != null)
                .collect(Collectors.toMap(ConnectorMappingDTO::getMapping, ConnectorMappingDTO::getColumn, (a, b) -> a));
    }

    private List<Map<String, Object>> transform(HarmonizationDTO harmonization, List<ConnectorTransformerDTO> transformers,
                                                List<Map<String, Object>> rows, String keycloakId) {
        for (ConnectorTransformerDTO transformer : transformers) {
            rows = transformer.isAppBased()
                    ? appPreviewBO.transformAppBased(transformer, new ArrayList<>(rows), keycloakId, step -> {})
                    : transformationBO.applyBuiltInTransformation(transformer, new ArrayList<>(rows), harmonization.getSourceKey());
        }
        return rows;
    }

    private List<Map<String, Object>> sourcePreview(HarmonizationDTO harmonization, String keycloakId) {
        return fileBO.previewRows(harmonization.getSourceFileId(), harmonization.getSourceMergeConfig(), PREVIEW_ROWS, keycloakId);
    }
}

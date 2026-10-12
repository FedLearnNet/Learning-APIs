package bio.cosy.feddb.local.agent.etl.transform.organizer;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypeNodeDTO;
import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.agent.etl.transform.config.ColumnMatcherConfig;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerHelper;
import bio.cosy.feddb.local.agent.etl.transform.model.CandidateAssignment;
import bio.cosy.feddb.local.agent.etl.transform.model.ColumnCandidate;
import bio.cosy.feddb.local.agent.text.SapbertSimilartity;
import bio.cosy.feddb.local.api.importer.files.table.TableSample;
import bio.cosy.feddb.local.api.importer.validation.ConnectorValidationBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import bio.cosy.feddb.local.api.schema.datatype.DataTypeEntityMapper;
import bio.cosy.feddb.local.api.schema.schemanode.LocalSchemaNodeWithPathDTO;
import bio.cosy.feddb.local.helper.text.TextSimilarity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class ColumnMatcher {

    @Inject
    ColumnMatcherConfig config;

    @Inject
    SapbertSimilartity sapbertSimilartity;

    @Inject
    ConnectorValidationBO validationBO;

    @Inject
    DataTypeEntityMapper dataTypeMapper;

    public Map<Long, List<ColumnCandidate>> match(TableSample sample,
                                                  List<LocalSchemaNodeWithPathDTO> schemaNodes,
                                                  Map<String, ColumnProfile> fileProfiles) {
        List<String> columns = sample.getColumns();
        Map<String, List<Object>> values = sample.getValues();
        double[][] semantic = sapbertSimilartity.semanticScoresNodeNames(columns, schemaNodes);

        Map<Long, List<ColumnCandidate>> candidates = new LinkedHashMap<>();
        for (int nodeIndex = 0; nodeIndex < schemaNodes.size(); nodeIndex++) {
            LocalSchemaNodeDTO node = schemaNodes.get(nodeIndex);
            candidates.put(node.getId(), candidates(node, columns, values, fileProfiles,
                    semantic == null ? null : semantic[nodeIndex]));
        }
        markClearColumns(candidates);
        return candidates;
    }

    private List<ColumnCandidate> candidates(LocalSchemaNodeDTO node,
                                             List<String> columns,
                                             Map<String, List<Object>> values,
                                             Map<String, ColumnProfile> profiles,
                                             double[] semantic) {
        DataTypeDTO dataType = node.getDataType();
        List<ColumnCandidate> scored = new ArrayList<>();
        for (int columnIndex = 0; columnIndex < columns.size(); columnIndex++) {
            String column = columns.get(columnIndex);

            ColumnCandidate candidate = new ColumnCandidate();
            candidate.setColumn(column);
            candidate.setNameScore(TextSimilarity.nameSimilarity(column, node.getName()));
            if (semantic != null) {
                candidate.setSemanticScore(semantic[columnIndex]);
            }
            candidate.setValidRatio(validRatio(valuesOf(column, values, profiles), node.getId(), dataType));
            candidate.calculateAndSetScore(config.textWeight(), config.semanticBaseline());
            scored.add(candidate);
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(ColumnCandidate::getScore).reversed())
                .limit(config.candidates())
                .peek(candidate -> candidate.setSummary(summary(values, profiles, candidate)))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void markClearColumns(Map<Long, List<ColumnCandidate>> candidatesByNode) {
        List<CandidateAssignment> rankedAssignments = rankCandidateAssignments(candidatesByNode);
        Set<Long> assignedNodeIds = new HashSet<>();
        Set<String> assignedColumns = new HashSet<>();

        for (CandidateAssignment assignment : rankedAssignments) {
            Long nodeId = assignment.getNodeId();
            ColumnCandidate candidate = assignment.getCandidate();
            if (assignedNodeIds.contains(nodeId) || assignedColumns.contains(candidate.getColumn())) {
                continue;
            }

            // Reserve the pair even if it is not clear enough to skip the column bot.
            assignedNodeIds.add(nodeId);
            assignedColumns.add(candidate.getColumn());

            List<ColumnCandidate> nodeCandidates = candidatesByNode.get(nodeId);
            nodeCandidates.remove(candidate);
            nodeCandidates.addFirst(candidate);
            double runnerUpScore = nodeCandidates.size() > 1 ? nodeCandidates.get(1).getScore() : 0;
            double runnerUpText = nodeCandidates.stream().skip(1)
                    .mapToDouble(other -> other.getTextScore(config.semanticBaseline())).max().orElse(0);
            candidate.setClear(runnerUpScore, runnerUpText, config.semanticBaseline(), config.confidentText(),
                    config.confidentMargin());
        }
    }

    private List<CandidateAssignment> rankCandidateAssignments(Map<Long, List<ColumnCandidate>> candidatesByNode) {
        List<CandidateAssignment> assignments = new ArrayList<>();
        candidatesByNode.forEach((nodeId, candidates) -> {
            for (ColumnCandidate candidate : candidates) {
                assignments.add(new CandidateAssignment(nodeId, candidate));
            }
        });
        assignments.sort(Comparator.comparingDouble(
                (CandidateAssignment assignment) -> assignment.getCandidate().getScore()).reversed());
        return assignments;
    }

    private static List<Object> valuesOf(String column, Map<String, List<Object>> values, Map<String, ColumnProfile> profiles) {
        List<String> fileValues = TransformerHelper.fileValues(profiles.get(column));
        return fileValues.isEmpty() ? values.get(column) : new ArrayList<>(fileValues);
    }

    private double validRatio(List<Object> values, Long schemaId, DataTypeDTO dataType) {
        if (dataType == null || values == null || values.isEmpty()) {
            return 0;
        }
        DataTypeNodeDTO nodeDTO = dataTypeMapper.dtoToNodeDto(dataType);
        long valid = values.stream()
                .filter(value -> validationBO.rejection(value, schemaId, nodeDTO) == null)
                .count();
        return (double) valid / values.size();
    }

    private String summary(
            Map<String, List<Object>> valueMap,
            Map<String, ColumnProfile> profiles, ColumnCandidate candidate) {
        ColumnProfile profile = profiles.get(candidate.getColumn());
        List<Object> values = valueMap.get(candidate.getColumn());
        StringBuilder summary = new StringBuilder();
        if (profile != null) {
            summary.append(profile.type()).append(", ").append(profile.uniqueValues()).append(" distinct ")
                    .append(TransformerHelper.describeFileValues(profile, config.summaryValues()));
        } else {
            summary.append("values: ").append(values == null ? "" : values.stream()
                    .map(String::valueOf)
                    .filter(value -> !value.isBlank())
                    .limit(config.summaryValues())
                    .collect(Collectors.joining(" | ")));
        }

        return summary.append(", ").append(Math.round(candidate.getValidRatio() * 100))
                .append("% of the values fit the schema node unconverted")
                .toString();
    }
}

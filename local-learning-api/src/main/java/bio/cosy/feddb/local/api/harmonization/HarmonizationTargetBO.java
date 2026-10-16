package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.local.agent.etl.transform.helper.AgentDataPrivacy;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class HarmonizationTargetBO {

    private static final int STATIC_EVIDENCE = 10;

    @Inject
    HarmonizationFileBO fileBO;

    @Inject
    HarmonizationSchemaBot schemaBot;

    @Inject
    HarmonizationConnectorMapper connectorMapper;

    @Inject
    AgentDataPrivacy dataPrivacy;

    public List<HarmonizationTargetDTO> targets(Long fileId, String key, String keycloakId) {
        Set<String> staticColumns = staticColumns(fileBO.rows(fileId, null), key);
        List<HarmonizationTargetDTO> targets = dataPrivacy.profiles(fileBO.profilesByName(fileId, keycloakId)).values().stream()
                .filter(profile -> !profile.name().equals(key))
                .map(profile -> connectorMapper.toTarget(profile, staticColumns.contains(profile.name())))
                .toList();
        HarmonizationColumnMeaningsDTO described = describe(targets);
        Map<String, String> meanings = Optional.ofNullable(described.getColumns()).orElse(List.of()).stream()
                .filter(meaning -> meaning.getColumn() != null && meaning.getDescription() != null)
                .collect(Collectors.toMap(HarmonizationColumnMeaningDTO::getColumn, HarmonizationColumnMeaningDTO::getDescription,
                        (a, b) -> a));
        targets.forEach(target -> Optional.ofNullable(meanings.get(target.getName())).ifPresent(meaning ->
                target.getDataType().setDescription(meaning.strip() + " " + target.getDataType().getDescription())));
        Map<String, Set<Object>> observed = targets.stream()
                .filter(target -> target.getDataType().getAllowedValues() != null)
                .collect(Collectors.toMap(HarmonizationTargetDTO::getName, target -> new LinkedHashSet<>(target.getDataType().getAllowedValues())));
        Optional.ofNullable(described.getSameKind()).orElse(List.of()).stream().filter(Objects::nonNull).forEach(group ->
                targets.stream().filter(target -> group.contains(target.getName()))
                        .forEach(target -> addValuesOfTheSameKind(target, group, observed)));
        return targets;
    }

    /**
     * The reference is a sample, so a value the columns of the same kind show, e.g. 'Up' of another drug, is a value
     * of this column too; only columns sharing a value with it count, so no value is made up.
     */
    static void addValuesOfTheSameKind(HarmonizationTargetDTO target, List<String> sameKind, Map<String, Set<Object>> observed) {
        Set<Object> own = observed.get(target.getName());
        if (own == null || sameKind == null) {
            return;
        }
        List<Object> added = sameKind.stream()
                .map(observed::get)
                .filter(values -> values != null && values.stream().anyMatch(own::contains))
                .flatMap(Set::stream)
                .filter(value -> !own.contains(value))
                .distinct()
                .toList();
        if (!added.isEmpty()) {
            target.getDataType().getAllowedValues().addAll(added);
            target.getDataType().setDescription(target.getDataType().getDescription() + " Columns of the same kind also hold "
                    + added + ", so these values are allowed as well.");
        }
    }

    private HarmonizationColumnMeaningsDTO describe(List<HarmonizationTargetDTO> targets) {
        try {
            return Objects.requireNonNullElseGet(schemaBot.describe(targets.stream()
                    .map(target -> target.getName() + ": " + target.getDataType().getDescription())
                    .toList()), HarmonizationColumnMeaningsDTO::new);
        } catch (RuntimeException e) {
            Log.warnf("The reference columns are described by their values only, no meaning was found: %s", e.getMessage());
            return new HarmonizationColumnMeaningsDTO();
        }
    }

    private Set<String> staticColumns(List<Map<String, Object>> rows, String key) {
        Map<Object, Map<String, Object>> first = new HashMap<>();
        Set<Object> repeated = new HashSet<>();
        Set<String> changing = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> seen = first.putIfAbsent(row.get(key), row);
            if (seen != null) {
                repeated.add(row.get(key));
                row.forEach((column, value) -> {
                    if (!Objects.equals(seen.get(column), value)) {
                        changing.add(column);
                    }
                });
            }
        }
        if (repeated.size() < STATIC_EVIDENCE) {
            return Set.of();
        }
        Set<String> staticColumns = new HashSet<>(rows.getFirst().keySet());
        staticColumns.removeAll(changing);
        return staticColumns;
    }
}

package bio.cosy.feddb.local.api.harmonization;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.HashMap;
import java.util.HashSet;
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

    public List<HarmonizationTargetDTO> targets(Long fileId, String key, String keycloakId) {
        Set<String> staticColumns = staticColumns(fileBO.rows(fileId, null), key);
        List<HarmonizationTargetDTO> targets = fileBO.profiles(fileId, keycloakId).stream()
                .filter(profile -> profile != null && !profile.name().equals(key))
                .map(profile -> connectorMapper.toTarget(profile, staticColumns.contains(profile.name())))
                .toList();
        Map<String, String> meanings = meanings(targets);
        targets.forEach(target -> Optional.ofNullable(meanings.get(target.getName())).ifPresent(meaning ->
                target.getDataType().setDescription(meaning.strip() + " " + target.getDataType().getDescription())));
        return targets;
    }

    private Map<String, String> meanings(List<HarmonizationTargetDTO> targets) {
        try {
            return schemaBot.describe(targets.stream()
                            .map(target -> target.getName() + ": " + target.getDataType().getDescription())
                            .toList()).getColumns().stream()
                    .filter(meaning -> meaning.getColumn() != null && meaning.getDescription() != null)
                    .collect(Collectors.toMap(HarmonizationColumnMeaningDTO::getColumn, HarmonizationColumnMeaningDTO::getDescription,
                            (a, b) -> a));
        } catch (RuntimeException e) {
            Log.warnf("The reference columns are described by their values only, no meaning was found: %s", e.getMessage());
            return Map.of();
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

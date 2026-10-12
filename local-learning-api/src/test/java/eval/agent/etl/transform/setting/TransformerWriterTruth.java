package eval.agent.etl.transform.setting;

import bio.cosy.feddb.core.helper.ResourceLoader;
import bio.cosy.feddb.local.api.harmonization.HarmonizationProfileHelper;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.run.ConnectorRunDTO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeBO;
import com.fasterxml.jackson.databind.ObjectMapper;
import eval.agent.etl.transform.pipeline.TransformerWriterImporter;
import eval.agent.etl.transform.pipeline.TransformerWriterUploader;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@ApplicationScoped
public class TransformerWriterTruth {

    private static final String KEY = "encounter_id";
    private static final Pattern SITE = Pattern.compile("site_(\\d+)\\.csv$");
    private static final String CLINIC_CONNECTOR = "/connectors/us-130-clinics.json";
    private static final String ORIGINAL_COHORT = " (original)";
    private static final String MISSING = "?";

    @Inject
    TransformerWriterUploader uploader;

    @Inject
    TransformerWriterImporter importer;

    @Inject
    SchemaNodeBO schemaNodeBO;

    @Inject
    ObjectMapper objectMapper;

    private final Map<String, Map<String, Map<String, Long>>> values = new ConcurrentHashMap<>();

    private static Optional<String> originalFile(TransformerWriterDataset dataset) {
        Matcher site = SITE.matcher(dataset.getDataPath());
        return site.find() ? Optional.of("/connectors/clinic_%03d.csv".formatted(Integer.parseInt(site.group(1)))) : Optional.empty();
    }

    public Map<String, Map<String, Long>> values(TransformerWriterDataset dataset) {
        return values.computeIfAbsent(dataset.getName(), name -> originalFile(dataset)
                .map(path -> importValues(dataset, path))
                .orElse(Map.of()));
    }

    public static Map<String, Map<String, String>> originalRows(TransformerWriterDataset dataset) {
        return originalFile(dataset).map(path -> byKey(readCsv(resource(path)))).orElse(Map.of());
    }

    private static File resource(String path) {
        try {
            return ResourceLoader.loadAsFile(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }


    public static Double wrongRows(Map<String, Map<String, String>> original, Map<String, Map<String, String>> harmonized,
                                   String column) {
        return original.isEmpty() ? null : (double) original.entrySet().stream()
                .filter(row -> !harmonized.containsKey(row.getKey()) || !Objects.equals(
                        normalize(row.getValue().get(column)), normalize(harmonized.get(row.getKey()).get(column))))
                .count() / original.size();
    }

    /** The rows of a CSV file by encounter_id. */
    public static Map<String, Map<String, String>> byKey(List<Map<String, String>> rows) {
        return rows.stream().collect(Collectors.toMap(row -> Objects.toString(normalize(row.get(KEY)), ""), Function.identity(),
                (first, second) -> first, LinkedHashMap::new));
    }

    public static List<Map<String, String>> readCsv(File file) {
        // The original files and the harmonized output are comma separated
        try (Reader reader = Files.newBufferedReader(file.toPath())) {
            return CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader).stream()
                    .map(CSVRecord::toMap)
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, Map<String, Long>> importValues(TransformerWriterDataset dataset, String originalFile) {
        TransformerWriterDataset original = new TransformerWriterDataset(dataset.getSchemaId(), dataset.getName() + ORIGINAL_COHORT,
                originalFile, ",", dataset.getPatientIdColumn(), null);
        ConnectorDTO connector;
        try {
            connector = uploader.upload(original);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ConnectorRunDTO run = importer.importWith(connector.getId(), connector(CLINIC_CONNECTOR));
        Log.infof("[ETL-Eval:truth] %s: the original file %s imported with the clinic connector: %s", dataset.getName(),
                originalFile, importer.describe(run, connector.getCohortId()));
        Map<String, Map<String, Long>> correct = new HashMap<>();
        QuarkusTransaction.requiringNew().call(() -> schemaNodeBO.getSchemaNodesWithPathForCohort(connector.getCohortId()))
                .forEach(node -> correct.put(node.getPath(),
                        normalized(importer.storedValues(connector.getCohortId(), node.getId()))));
        correct.values().removeIf(Map::isEmpty);
        return correct;
    }

    public static Double shift(Map<String, Long> produced, Map<String, Long> truth) {
        return truth == null || truth.isEmpty() ? null : distance(normalized(produced), truth);
    }

    public static double distance(Map<String, Long> produced, Map<String, Long> expected) {
        long producedTotal = produced.values().stream().mapToLong(Long::longValue).sum();
        long expectedTotal = expected.values().stream().mapToLong(Long::longValue).sum();
        if (producedTotal == 0) {
            return 1.0;
        }
        return 0.5 * Stream.concat(produced.keySet().stream(), expected.keySet().stream()).distinct()
                .mapToDouble(key -> Math.abs((double) produced.getOrDefault(key, 0L) / producedTotal
                        - (double) expected.getOrDefault(key, 0L) / expectedTotal))
                .sum();
    }

    private static Map<String, Long> normalized(Map<String, Long> counts) {
        Map<String, Long> normalized = new HashMap<>();
        counts.forEach((value, count) -> Optional.ofNullable(normalize(value))
                .ifPresent(key -> normalized.merge(key, count, Long::sum)));
        return normalized;
    }

    private static String normalize(Object value) {
        // The importer eval writes stored values as 'value' and empty ones as (empty)
        String text = StringUtils.unwrap(Objects.toString(value, "").strip(), '\'').strip();
        if (text.isEmpty() || text.equals(HarmonizationProfileHelper.EMPTY) || text.equals(MISSING)) {
            return null;
        }
        try {
            return new BigDecimal(text).stripTrailingZeros().toPlainString();
        } catch (NumberFormatException e) {
            return text;
        }
    }

    private ConnectorDTO connector(String path) {
        try {
            return objectMapper.readValue(ResourceLoader.loadAsString(path), ConnectorDTO.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

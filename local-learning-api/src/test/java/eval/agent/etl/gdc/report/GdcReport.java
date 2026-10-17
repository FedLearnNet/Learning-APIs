package eval.agent.etl.gdc.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import eval.agent.etl.gdc.pojo.GdcFieldResult;
import eval.agent.etl.gdc.pojo.GdcVerdict;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Schema matching of the GDC benchmark: recall is the share of the fields of the ground truth mapped from one of its
 * columns, precision the share of the mapped fields that are correct; unlike the ranking matchers of the benchmark
 * (e.g. Magneto, Recall@k), the agent answers with one mapping per field, so these are top-1 values.
 */
public final class GdcReport {

    public static final String RESULTS_FILE = "gdc-benchmark.json";
    private static final String REPORT_FILE = "gdc-benchmark-report.md";
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private GdcReport() {
    }

    public static void save(Path directory, String label, Map<String, List<GdcFieldResult>> results) throws IOException {
        Files.createDirectories(directory);
        JSON.writeValue(directory.resolve(RESULTS_FILE).toFile(), results);
        StringBuilder text = new StringBuilder("# GDC schema matching benchmark (").append(label).append(")\n\n")
                .append("The ten studies of GDC-SM (https://doi.org/10.5281/zenodo.14963587) are mapped by the assistant onto ")
                .append("the GDC schema (the 78 fields of the ground truth, or all 736 like the benchmark). Top-1 values: the agent maps every field from ")
                .append("one chain, a field is correct when that chain reads a column the ground truth names for it. ")
                .append("The ground truth lists only some right mappings, so an LLM judge rates the extras; the judged ")
                .append("precision also counts the extras it finds right.\n\n")
                .append("| Study | Fields | Correct | Wrong column | Missed | Extra | Extra judged right | Recall | Precision | F1 | Judged precision |\n")
                .append("|---|---|---|---|---|---|---|---|---|---|---|\n");
        results.forEach((study, fields) -> text.append(row(study, fields)));
        text.append(row("**All**", results.values().stream().flatMap(List::stream).toList()));
        text.append("\n## Not correct\n\n| Study | Field | Ground truth | Mapped | Verdict | Configuration | Judge |\n|---|---|---|---|---|---|---|\n");
        results.values().stream().flatMap(List::stream)
                .filter(field -> field.getVerdict() != GdcVerdict.CORRECT && field.getVerdict() != GdcVerdict.EMPTY)
                .forEach(field -> text.append("| ").append(field.getStudy()).append(" | ").append(field.getField())
                        .append(" | ").append(String.join(", ", field.getExpected())).append(" | ")
                        .append(String.join(", ", field.getMapped())).append(" | ").append(field.getVerdict()).append(" | ")
                        .append(cell(field.getConfiguration())).append(" | ").append(field.getJudged() == null ? ""
                                : (field.getJudged() ? "right: " : "wrong: ") + cell(field.getJudgeReason())).append(" |\n"));
        Files.writeString(directory.resolve(REPORT_FILE), text);
    }

    private static String row(String study, List<GdcFieldResult> fields) {
        Map<GdcVerdict, Long> counts = fields.stream().collect(Collectors.groupingBy(GdcFieldResult::getVerdict, Collectors.counting()));
        long correct = counts.getOrDefault(GdcVerdict.CORRECT, 0L);
        long truth = correct + counts.getOrDefault(GdcVerdict.WRONG_COLUMN, 0L) + counts.getOrDefault(GdcVerdict.MISSED, 0L);
        long mapped = correct + counts.getOrDefault(GdcVerdict.WRONG_COLUMN, 0L) + counts.getOrDefault(GdcVerdict.EXTRA, 0L);
        double recall = truth == 0 ? 0 : (double) correct / truth;
        double precision = mapped == 0 ? 0 : (double) correct / mapped;
        double f1 = recall + precision == 0 ? 0 : 2 * recall * precision / (recall + precision);
        long judged = fields.stream().filter(field -> Boolean.TRUE.equals(field.getJudged())).count();
        double judgedPrecision = mapped == 0 ? 0 : (double) (correct + judged) / mapped;
        return String.format(Locale.ROOT, "| %s | %d | %d | %d | %d | %d | %d | %.2f | %.2f | %.2f | %.2f |%n", study, truth,
                correct, counts.getOrDefault(GdcVerdict.WRONG_COLUMN, 0L), counts.getOrDefault(GdcVerdict.MISSED, 0L),
                counts.getOrDefault(GdcVerdict.EXTRA, 0L), judged, recall, precision, f1, judgedPrecision);
    }

    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "/").replace("\n", " ");
    }
}

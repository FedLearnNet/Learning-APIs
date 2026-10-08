package eval.agent.etl.transform.report;

import bio.cosy.feddb.local.agent.etl.transform.model.VisitMapping;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import eval.agent.etl.transform.judge.TransformerWriterVerdict;
import eval.agent.etl.transform.pojo.TransformerWriterResult;
import eval.agent.etl.transform.pojo.TransformerWriterReview;
import eval.agent.etl.transform.pojo.TransformerWriterTransformation;
import io.quarkus.logging.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

public final class TransformerWriterReport {

    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final StringBuilder text = new StringBuilder();

    private TransformerWriterReport() {
    }

    public static void save(String model, Map<String, List<TransformerWriterResult>> resultsByDataset) throws IOException {
        Path directory = Path.of("results", "eval", model.replace(":latest", ""));
        Files.createDirectories(directory);
        JSON.writeValue(directory.resolve("transformer-writer.json").toFile(), resultsByDataset);
        Files.writeString(directory.resolve("transformer-writer-report.md"), write(model, resultsByDataset),
                StandardCharsets.UTF_8);
        Log.infof("[ETL-Eval:report] Saved the results to %s", directory.toAbsolutePath());
    }

    private static String write(String model, Map<String, List<TransformerWriterResult>> resultsByDataset) {
        TransformerWriterReport report = new TransformerWriterReport();
        report.line("# Transformer assistant evaluation (" + model + ")");
        report.line("");
        report.line("Every dataset is uploaded, mapped by the assistant and imported completely. Mapping / transformation "
                + "correct by the LLM judge: ✓ / ✗, - not judged.");
        resultsByDataset.forEach(report::dataset);
        List<TransformerWriterResult> results = resultsByDataset.values().stream().flatMap(Collection::stream).toList();
        report.summary(results);
        report.mappingErrors(results);
        return report.text.toString();
    }

    private void dataset(String dataset, List<TransformerWriterResult> results) {
        line("");
        line("## " + dataset);
        line("");
        if (!results.isEmpty()) {
            line("Import " + results.getFirst().getReview().getImportRun());
            line("");
        }
        line("| Schema node | Mapped column | Transformation | Visit | Imported | Rejected | LLM judge |");
        line("|---|---|---|---|---|---|---|");
        for (TransformerWriterResult result : results) {
            TransformerWriterReview review = result.getReview();
            line("| %s | %s | %s | %s | %s | %s | %s |".formatted(
                    review.getSchemaNode(),
                    review.isFound() ? review.getMappedColumn() : "not found",
                    kind(review),
                    visit(review.getVisitMapping()),
                    review.isFound() ? String.valueOf(review.getImportedEntries()) : "-",
                    review.isFound() ? String.valueOf(review.getRejectedValues()) : "-",
                    flags(result.getVerdict())));
        }
    }

    private void summary(List<TransformerWriterResult> results) {
        List<TransformerWriterReview> reviews = results.stream().map(TransformerWriterResult::getReview).toList();
        List<TransformerWriterVerdict> verdicts = results.stream().map(TransformerWriterResult::getVerdict)
                .filter(Objects::nonNull).toList();
        long correct = count(verdicts, TransformerWriterVerdict::isCorrect);
        line("");
        line("## Summary");
        line("");
        line("- %d schema nodes: %d mapped (%d with custom code), %d not found, %d with rejected values".formatted(
                reviews.size(),
                count(reviews, TransformerWriterReview::isFound),
                count(reviews, review -> kind(review).equals("custom code")),
                count(reviews, review -> !review.isFound()),
                count(reviews, review -> review.getRejectedValues() > 0)));
        line("- LLM judge: %d judged, %d correct (%.0f%%), mapping correct %d, transformation correct %d".formatted(
                verdicts.size(), correct, verdicts.isEmpty() ? 0.0 : 100.0 * correct / verdicts.size(),
                count(verdicts, TransformerWriterVerdict::isMappingCorrect),
                count(verdicts, TransformerWriterVerdict::isTransformationCorrect)));
    }

    private void mappingErrors(List<TransformerWriterResult> results) {
        line("");
        line("## Mapping errors");
        List<TransformerWriterResult> errors = results.stream()
                .filter(result -> result.getVerdict() != null && !result.getVerdict().isCorrect()
                        || result.getReview().getRejectedValues() > 0)
                .toList();
        if (errors.isEmpty()) {
            line("");
            line("None.");
        }
        errors.forEach(this::mappingError);
    }

    private void mappingError(TransformerWriterResult result) {
        TransformerWriterReview review = result.getReview();
        line("");
        line("### " + result.getDataset() + " / " + review.getSchemaNode());
        if (!review.isFound()) {
            line("- Not found: " + review.getNotFoundReason());
        }
        if (result.getVerdict() != null) {
            line("- LLM judge " + flags(result.getVerdict()) + ": " + result.getVerdict().getReason());
        }
        if (review.isFound()) {
            line("- Imported values: " + review.getImportedValues());
        }
        review.getRejections().forEach((message, count) -> line("- Rejected x" + count + ": " + message));
        review.getTransformations().forEach(this::transformation);
    }

    private void transformation(TransformerWriterTransformation transformation) {
        line("- Transformation: " + transformation.getConfiguration());
        if (transformation.getCode() != null) {
            line("```python");
            line(transformation.getCode());
            line("```");
        }
    }

    private static String kind(TransformerWriterReview review) {
        if (!review.isFound()) {
            return "-";
        }
        if (review.getTransformations().stream().anyMatch(transformation -> transformation.getCode() != null)) {
            return "custom code";
        }
        return review.getTransformations().isEmpty() ? "direct" : review.getTransformations().size() + " function(s)";
    }

    private static String visit(VisitMapping visit) {
        return visit == null ? "-" : visit.getVisitIdMapping() + " / " + visit.getVisitTimestampMapping();
    }

    private static String flags(TransformerWriterVerdict verdict) {
        return verdict == null ? "-" : mark(verdict.isMappingCorrect()) + " / " + mark(verdict.isTransformationCorrect());
    }

    private static String mark(boolean correct) {
        return correct ? "✓" : "✗";
    }

    private static <T> long count(List<T> items, Predicate<T> predicate) {
        return items.stream().filter(predicate).count();
    }

    private void line(String line) {
        text.append(line).append('\n');
    }
}

package eval.agent.etl.harmonization.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import eval.agent.etl.harmonization.pojo.HarmonizationFileResult;
import eval.agent.etl.transform.report.TransformerWriterReport;
import io.quarkus.logging.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class HarmonizationFileReport {

    public static final String RESULTS_FILE = "harmonization-file.json";
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    /** From this shift on the harmonized values of a column are clearly not distributed like the reference ones. */
    private static final double SUSPICIOUS_SHIFT = 0.3;

    private HarmonizationFileReport() {
    }

    public static void save(Path directory, String label, String reference, List<HarmonizationFileResult> results)
            throws IOException {
        Files.createDirectories(directory);
        JSON.writeValue(directory.resolve(RESULTS_FILE).toFile(), results);
        Files.writeString(directory.resolve("harmonization-file-report.md"), write(label, reference, results),
                StandardCharsets.UTF_8);
        Log.infof("[Harmonization-Eval:report] Saved the results to %s", directory.toAbsolutePath());
    }

    private static String write(String model, String reference, List<HarmonizationFileResult> results) {
        StringBuilder text = new StringBuilder("# File harmonization evaluation (" + model + ")\n\n")
                .append("The columns of ").append(reference).append(" are profiled into the target columns, every dataset ")
                .append("is mapped onto them by the assistant like in the frontend, saved, which combines equal steps, and ")
                .append("harmonized. The judge reviews every column; the shift compares the harmonized values with the ")
                .append("reference ones (share of category values that differ, or mean difference in standard deviations).\n\n")
                .append("| Dataset | Mapped | Correct by the gold standard | Judged correct | Steps (suggested → combined) | Rows "
                        + "| Keys not in the reference | Rejected values | Mean shift |\n|---|---|---|---|---|---|---|---|---|\n");
        results.forEach(result -> text.append("| ").append(result.getDataset())
                .append(" | ").append(result.mapped()).append(" / ").append(result.getTargets())
                .append(" | ").append(result.truthCompared() == 0 ? "-" : result.truthCorrect() + " / " + result.truthCompared())
                .append(" | ").append(result.correct()).append(" / ").append(result.getTargets())
                .append(" | ").append(result.getSuggestedTransformers()).append(" → ").append(result.getTransformers())
                .append(" | ").append(result.getRows())
                .append(" | ").append(result.getUnmatched())
                .append(" | ").append(result.rejected())
                .append(" | ").append(String.format("%.2f", result.shift()))
                .append(" |\n"));
        for (HarmonizationFileResult result : results) {
            text.append("\n## ").append(result.getDataset())
                    .append("\n\n| Column | Mapped from | Wrong rows (gold standard) | Shift | Empty (reference) | Rejected | Judge |\n|---|---|---|---|---|---|---|\n");
            result.getColumns().forEach(column -> text.append("| ").append(column.getColumn())
                    .append(" | ").append(column.getReview().isFound() ? cell(column.getReview().getMappedColumn()) : "✗ not mapped")
                    .append(" | ").append(column.getTruthShift() == null ? "-" : String.format("%.2f %s", column.getTruthShift(),
                            column.getTruthShift() <= TransformerWriterReport.CORRECT_SHIFT ? "✓" : "✗"))
                    .append(" | ").append(shift(column.getShift()))
                    .append(" | ").append(percent(column.getEmpty())).append(" (").append(percent(column.getReferenceEmpty())).append(')')
                    .append(" | ").append(column.getRejected())
                    .append(" | ").append(column.getVerdict() == null ? "-" : (column.correct() ? "✓ " : "✗ ")
                            + cell(column.getVerdict().getReason()))
                    .append(" |\n"));
        }
        return text.toString();
    }

    private static String shift(Double shift) {
        return shift == null ? "-" : String.format("%.2f", shift) + (shift >= SUSPICIOUS_SHIFT ? " ⚠" : "");
    }

    private static String percent(double share) {
        return Math.round(share * 100) + "%";
    }

    private static String cell(String text) {
        return text == null ? "" : text.replace("|", "\\|").replace("\n", " ");
    }
}

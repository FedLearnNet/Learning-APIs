package eval.agent.etl.transform.judge;

import eval.agent.etl.transform.pojo.TransformerWriterReview;
import eval.agent.etl.transform.pojo.TransformerWriterTransformation;

import java.util.List;
import java.util.stream.Collectors;

public final class TransformerWriterPrompt {

    private TransformerWriterPrompt() {
    }

    public static String render(TransformerWriterReview review) {
        StringBuilder text = new StringBuilder();
        appendSchemaNode(text, review);
        appendSourceColumns(text, review);
        if (review.isFound()) {
            appendMapping(text, review);
        } else {
            text.append("\n### Mapping\nThe agent found no mapping. Its reason: ").append(review.getNotFoundReason())
                    .append('\n');
        }
        appendImport(text, review);
        return text.toString();
    }

    private static void appendSchemaNode(StringBuilder text, TransformerWriterReview review) {
        text.append("### Schema node: ").append(review.getSchemaNode()).append('\n');
        if (review.getDescription() != null && !review.getDescription().isBlank()) {
            text.append("Description: ").append(review.getDescription()).append('\n');
        }
        text.append("Data type: ").append(review.getDataType()).append('\n');
    }

    private static void appendSourceColumns(StringBuilder text, TransformerWriterReview review) {
        text.append(review.isFound() ? "\n### Source columns\n" : "\n### Columns of the export\n");
        review.getSourceColumns().forEach(column -> text.append("- ").append(column).append('\n'));
    }

    private static void appendMapping(StringBuilder text, TransformerWriterReview review) {
        text.append("\n### Mapping\nMapped column: ").append(review.getMappedColumn()).append('\n');
        List<TransformerWriterTransformation> transformations = review.getTransformations();
        if (transformations.isEmpty()) {
            text.append("The column is mapped directly, without a transformation.\n");
        }
        for (int index = 0; index < transformations.size(); index++) {
            TransformerWriterTransformation transformation = transformations.get(index);
            text.append(index + 1).append(". ").append(transformation.getConfiguration()).append('\n');
            if (transformation.getCode() != null) {
                text.append("```python\n").append(transformation.getCode()).append("\n```\n");
            }
        }
    }

    private static void appendImport(StringBuilder text, TransformerWriterReview review) {
        text.append("\n### Import of the whole dataset\n").append(review.getImportRun()).append('\n');
        if (!review.isFound()) {
            return;
        }
        text.append(review.getImportedEntries()).append(" data entries: ")
                .append(review.getImportedValues().entrySet().stream()
                        .map(entry -> entry.getKey() + " x" + entry.getValue())
                        .collect(Collectors.joining(", ")))
                .append('\n');
        review.getRejections().forEach((message, count) ->
                text.append("- rejected x").append(count).append(": ").append(message).append('\n'));
    }
}

package bio.cosy.feddb.local.agent.etl.transform.helper;

import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.model.VisitMapping;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import io.quarkus.logging.Log;

import java.util.stream.Collectors;

public final class EtlAgentLog {

    private static final int MAX_TEXT = 160;

    public static void step(String step, String format, Object... args) {
        Log.infof("[%s] %s", step, String.format(format, args));
    }

    public static String describe(ConnectorSelectedTransformer selected) {
        if (selected == null) {
            return "nothing";
        }
        if (!selected.isFoundTransformer()) {
            return "nothing: " + shorten(selected.getNeededTransformerFunction());
        }
        if (selected.getSelectedTransformers().isEmpty()) {
            return "'" + selected.getMappedColumn() + "' directly";
        }
        return "'" + selected.getMappedColumn() + "' with " + selected.getSelectedTransformers().stream()
                .map(transformer -> transformer.getMethodName() + " (" + kind(transformer) + ")")
                .collect(Collectors.joining(", "));
    }

    public static String kind(ConnectorTransformerDTO transformer) {
        if (transformer.isCodeTransformer()) {
            return "custom code";
        }
        return transformer.isStoreTool() ? "ETL tool" : "native";
    }

    public static String describe(VisitMapping visit) {
        if (visit == null) {
            return "";
        }
        return ", visit '" + visit.getVisitIdMapping() + "' at '" + visit.getVisitTimestampMapping() + "'"
                + (visit.getTimestampFormat() == null ? "" : " (" + visit.getTimestampFormat() + ")");
    }

    public static String shorten(String text) {
        if (text == null) {
            return "";
        }
        String line = text.strip().lines().map(String::strip).collect(Collectors.joining(" "));
        return line.length() > MAX_TEXT ? line.substring(0, MAX_TEXT) + " ..." : line;
    }
}

package eval.agent.etl.transform.setting;

import bio.cosy.feddb.local.agent.etl.transform.organizer.TransformerOrganizer;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import eval.agent.etl.transform.judge.TransformerWriterVerdict;
import eval.agent.etl.transform.pojo.TransformerWriterReview;
import io.quarkus.logging.Log;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class EvaluationRun {

    private static final ObjectMapper JSON = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private EvaluationRun() {
    }

    public static Path directory(String model) {
        return Path.of("results", "eval", model.replace(":latest", ""), mode(), "run-" + run());
    }

    public static String label(String model) {
        return model + ", " + mode() + " data, run " + run();
    }

    public static <T> T load(Path file, TypeReference<T> type, T empty) {
        if (Boolean.getBoolean("eval.replace") || !Files.exists(file)) {
            return empty;
        }
        try {
            T results = JSON.readValue(file.toFile(), type);
            Log.infof("[Eval] Continuing the results of %s", file.toAbsolutePath());
            return results;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean failed(TransformerWriterReview review, TransformerWriterVerdict verdict) {
        return verdict == null || review.getNotFoundReason() != null
                && review.getNotFoundReason().startsWith(TransformerOrganizer.SELECTION_FAILED);
    }

    public static void markComplete(Path directory, String test) throws IOException {
        Files.writeString(directory.resolve(test + ".done"), System.getProperty("eval.datasets", ""));
    }

    private static String mode() {
        String data = flag("flnet.connector.agent.synthetic-data.enabled") ? "synthetic" : "real";
        return flag("flnet.connector.agent.use-statistic-privacy") ? data + "-privacy" : data;
    }

    private static String run() {
        return System.getProperty("eval.run", "1");
    }

    private static boolean flag(String name) {
        return ConfigProvider.getConfig().getOptionalValue(name, Boolean.class).orElse(false);
    }
}

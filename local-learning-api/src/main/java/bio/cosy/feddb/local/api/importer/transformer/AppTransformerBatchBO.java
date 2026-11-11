package bio.cosy.feddb.local.api.importer.transformer;

import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.AppMessageTypeEnum;
import bio.cosy.feddb.core.execution.run.types.AppRunTypeEnum;
import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.files.table.TableDataGroup;
import bio.cosy.feddb.local.api.importer.run.AppTransformerSessionFailure;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@ApplicationScoped
public class AppTransformerBatchBO {
    @Inject
    ObjectMapper objectMapper;

    @Inject
    FLNetClientConfig config;

    @Inject
    @ConfigProperty(name = "quarkus.websockets-next.server.max-message-size", defaultValue = "2147483647")
    long websocketMessageBytes = Integer.MAX_VALUE;

    @Inject
    @ConfigProperty(name = "quarkus.websockets-next.server.max-frame-size", defaultValue = "2147483647")
    long websocketFrameBytes = Integer.MAX_VALUE;

    public int maxPatients() {
        int limit = config.connector().appTransformBatchPatients();
        if (limit < 1) {
            throw new IllegalArgumentException("flnet.connector.app-transform-batch-patients must be positive");
        }
        return limit;
    }

    public long maxMessageBytes() {
        long limit = config.connector().appTransformMaxMessageBytes();
        if (limit <= 0) {
            throw new IllegalArgumentException("flnet.connector.app-transform-max-message-bytes must be positive");
        }
        return Math.min(limit, Math.min(websocketMessageBytes, websocketFrameBytes));
    }

    public long rowsBytes(List<Map<String, Object>> rows) {
        return jsonBytes(rows).length;
    }

    /** Reserve the longest possible batch id, so every actual message fits this budget. */
    public long rowsBudget(ConnectorTransformerDTO transformer) {
        long overhead = jsonBytes(messageOf(Long.MIN_VALUE, transformer, List.of())).length - 2L;
        long budget = maxMessageBytes() - overhead;
        if (budget < 2) {
            throw new AppTransformerSessionFailure("App transformer '" + transformer.getAppImage()
                    + "' hyperparameters and message envelope exceed the " + maxMessageBytes() + " byte message limit");
        }
        return budget;
    }

    public String serialize(Long batchId, ConnectorTransformerDTO transformer, List<Map<String, Object>> rows) {
        byte[] payload = jsonBytes(messageOf(batchId, transformer, rows));
        if (payload.length > maxMessageBytes()) {
            throw new AppTransformerSessionFailure("App transformer '" + transformer.getAppImage()
                    + "' message needs " + payload.length + " UTF-8 bytes, exceeding the " + maxMessageBytes()
                    + " byte limit. A patient's rows cannot be split; increase the limit or reduce the patient data.");
        }
        return new String(payload, StandardCharsets.UTF_8);
    }

    public Reader reader(Callable<TableDataGroup> next, ConnectorDTO connector) {
        long budget = connector.getAppTransformers().stream().mapToLong(this::rowsBudget).min().orElseThrow();
        return reader(next, maxPatients(), budget);
    }

    public Reader reader(Callable<TableDataGroup> next, ConnectorTransformerDTO transformer, boolean batchable) {
        return reader(next, batchable ? maxPatients() : 1, rowsBudget(transformer));
    }

    public Reader reader(Callable<TableDataGroup> next, int maxPatients, long maxRowBytes) {
        if (maxPatients < 1 || maxRowBytes < 2) {
            throw new IllegalArgumentException("Patient batch limits must be positive");
        }
        return new Reader(next, maxPatients, maxRowBytes);
    }

    private AppMessageWrapperDTO<StartRunDTO> messageOf(Long batchId, ConnectorTransformerDTO transformer,
                                                       List<Map<String, Object>> rows) {
        StartRunDTO start = new StartRunDTO();
        start.setId(batchId);
        start.setStatus(RunStatusTypes.PENDING);
        start.setHyperParams(transformer.getHyperparams());
        LinkedHashMap<String, Object> inputData = new LinkedHashMap<>();
        inputData.put("input", rows);
        start.setInputData(inputData);
        start.setInputFilePaths(new LinkedHashMap<>());
        return new AppMessageWrapperDTO<>(AppMessageTypeEnum.START_PREDICTION, start, AppRunTypeEnum.MODEL_RUN);
    }

    private byte[] jsonBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new AppTransformerSessionFailure("Could not serialize app transformer input", e);
        }
    }

    public final class Reader {
        private final Callable<TableDataGroup> next;
        private final int maxPatients;
        private final long maxRowBytes;
        private TableDataGroup pending;

        private Reader(Callable<TableDataGroup> next, int maxPatients, long maxRowBytes) {
            this.next = next;
            this.maxPatients = maxPatients;
            this.maxRowBytes = maxRowBytes;
        }

        public List<TableDataGroup> nextBatch() throws Exception {
            List<TableDataGroup> batch = new ArrayList<>();
            long bytes = 2; // JSON array brackets
            boolean hasRows = false;
            while (batch.size() < maxPatients) {
                TableDataGroup group = pending != null ? pending : next.call();
                pending = null;
                if (group == null) {
                    break;
                }
                long content = rowsBytes(group.rows()) - 2;
                long addition = content + (hasRows && !group.rows().isEmpty() ? 1 : 0);
                if (!batch.isEmpty() && bytes + addition > maxRowBytes) {
                    pending = group;
                    break;
                }
                batch.add(group);
                bytes += addition;
                hasRows |= !group.rows().isEmpty();
            }
            return batch;
        }
    }
}

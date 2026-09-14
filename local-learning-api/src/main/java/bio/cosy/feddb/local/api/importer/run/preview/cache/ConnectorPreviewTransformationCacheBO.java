package bio.cosy.feddb.local.api.importer.run.preview.cache;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.importer.connector.ConnectorConfigDTO;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.apache.commons.codec.digest.DigestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ConnectorPreviewTransformationCacheBO extends BaseBo<
        ConnectorPreviewTransformationCacheDTO,
        ConnectorPreviewTransformationCacheEntity,
        ConnectorPreviewTransformationCacheAO,
        ConnectorPreviewTransformationCacheMapper> {

    public static final int SOURCE_STEP = 0;

    public String sourceFingerprint(ConnectorConfigDTO config) {
        return hash(List.of(
                String.valueOf(config.getCohortId()),
                json(config.getInputConfig()),
                json(config.getMergeConfig()),
                json(config.getPivotConfig()),
                json(config.getFileInfo())));
    }

    public String stageFingerprint(String previousFingerprint, ConnectorTransformerDTO transformer) {
        return hash(List.of(previousFingerprint, json(transformer)));
    }

    @Transactional
    public Optional<ConnectorPreviewTransformationCacheStage> find(Long connectorId, int stepIndex, String fingerprint) {
        if (connectorId == null || fingerprint == null) {
            return Optional.empty();
        }
        return ao.find(connectorId, stepIndex, fingerprint)
                .map(entity -> new ConnectorPreviewTransformationCacheStage(
                        entity.getRows(), entity.getColumns(), entity.getUpdatedAt()));
    }


    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void store(Long connectorId,
                      int stepIndex,
                      Long transformerId,
                      String fingerprint,
                      List<Map<String, Object>> rows,
                      List<String> columns) {
        if (connectorId == null || fingerprint == null) {
            return;
        }
        try {
            ao.findStage(connectorId, stepIndex).ifPresent(ao::delete);
            ao.flush();

            ConnectorPreviewTransformationCacheDTO dto = new ConnectorPreviewTransformationCacheDTO();
            dto.setConnectorId(connectorId);
            dto.setTransformerId(transformerId);
            dto.setStepIndex(stepIndex);
            dto.setFingerprint(fingerprint);
            dto.setRows(rows);
            dto.setColumns(columns);
            dto.setRowCount(rows == null ? 0 : rows.size());
            create(dto);
        } catch (Exception e) {
            // A cache that cannot be written must never fail the preview it was meant to speed up.
            Log.warnf("Could not cache preview stage %d of connector %d: %s",
                    stepIndex, connectorId, e.getMessage());
        }
    }

    @Transactional
    public long invalidateFrom(Long connectorId, int stepIndex) {
        if (connectorId == null) {
            return 0;
        }
        long removed = ao.deleteFromStep(connectorId, Math.max(0, stepIndex));
        Log.debugf("Dropped %d cached preview stage(s) from step %d of connector %d",
                removed, stepIndex, connectorId);
        return removed;
    }

    @Transactional
    public long invalidateAll(Long connectorId) {
        if (connectorId == null) {
            return 0;
        }
        return ao.deleteForConnector(connectorId);
    }


    private String hash(List<String> parts) {
        return DigestUtils.sha256Hex(String.join(" ", parts));
    }


    private String json(Object value) {
        if (value == null) {
            return "";
        }
        try {
            return canonicalMapper().writeValueAsString(value);
        } catch (Exception e) {
            // Fall back to something stable rather than poisoning the key with a random identity.
            Log.debugf("Could not canonicalise %s for the preview cache key: %s",
                    value.getClass().getSimpleName(), e.getMessage());
            return value.toString();
        }
    }

    private ObjectMapper canonicalMapper() {
        return JsonMapper.builder()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .build();
    }
}

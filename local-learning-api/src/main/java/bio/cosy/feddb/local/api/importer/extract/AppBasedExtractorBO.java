package bio.cosy.feddb.local.api.importer.extract;

import bio.cosy.feddb.local.api.importer.connector.input.AppBasedUploadSettingsDTO;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.apache.commons.codec.digest.DigestUtils;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class AppBasedExtractorBO {

    private static final long CACHE_TTL_MILLIS = 60 * 60 * 1000L;

    /**
     * Finished results cache.
     * Key: request-hash
     */
    private final ConcurrentHashMap<String, ConnectorExtractorStreamDTO> cache = new ConcurrentHashMap<>();

    /**
     * Running streams deduplication.
     * Key: keycloakId + ":" + request-hash
     */
    private final ConcurrentHashMap<String, Multi<ConnectorExtractorStreamDTO>> inFlight = new ConcurrentHashMap<>();

    @Inject
    ConnectorExtractBO connectorExtractBO;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    AppExtractorMapper mapper;

    @ActivateRequestContext
    public Multi<ConnectorExtractorStreamDTO> start(AppBasedExtractorRequestDTO request, String keycloakId) {
        cleanupExpiredEntries();

        if (request == null) {
            throw new BadRequestException("Request must not be null");
        }
        if (keycloakId == null || keycloakId.isBlank()) {
            throw new BadRequestException("keycloakId must not be null or blank");
        }

        String hash = hashRequest(request);

        // 1) Finished cache is global by request hash
        ConnectorExtractorStreamDTO cachedEntry = cache.get(hash);
        if (cachedEntry != null) {
            ConnectorExtractorStreamDTO cachedDto = mapper.copy(cachedEntry);
            cachedDto.setCached(true);
            cachedDto.setHash(hash);
            return Multi.createFrom().item(cachedDto);
        }

        // 2) Running requests are user-specific
        String inFlightKey = buildInFlightKey(keycloakId, hash);

        return inFlight.computeIfAbsent(inFlightKey, ignored -> createInFlightStream(request, keycloakId, hash, inFlightKey));
    }

    public void invalidate(AppBasedExtractorRequestDTO request) {
        if (request == null) {
            return;
        }

        String hash = hashRequest(request);
        cache.remove(hash);

        // optional: also drop running entries for this hash, regardless of user
        inFlight.entrySet().removeIf(e -> e.getKey().endsWith(":" + hash));
    }

    public void invalidateAll() {
        cache.clear();
        inFlight.clear();
    }

    private Multi<ConnectorExtractorStreamDTO> createInFlightStream(
            AppBasedExtractorRequestDTO request,
            String keycloakId,
            String hash,
            String inFlightKey
    ) {
        AppBasedUploadSettingsDTO settings = new AppBasedUploadSettingsDTO();
        settings.setAppImage(request.getAppImage());
        settings.setAppVersionId(request.getAppVersionId());
        settings.setHyperParams(request.getHyperParams());

        Multi<ConnectorExtractorStreamDTO> upstream = connectorExtractBO.startFromApp(
                settings,
                request.getCohortId(),
                keycloakId
        );

        if (upstream == null) {
            throw new BadRequestException("Could not start app-based extractor");
        }

        AtomicReference<Multi<ConnectorExtractorStreamDTO>> self = new AtomicReference<>();

        Multi<ConnectorExtractorStreamDTO> shared = Multi.createBy().replaying().ofMulti(
                upstream
                        .onItem().invoke(dto -> {
                            if (dto != null) {
                                dto.setHash(hash);
                                dto.setCached(false);

                                // only cache "completed enough" payloads
                                if (dto.getUploadInfo() != null && !dto.getUploadInfo().isEmpty()) {
                                    cache.put(hash, mapper.copy(dto));
                                }
                            }
                        })
                        .onFailure().invoke(t -> inFlight.remove(inFlightKey, self.get()))
                        .onCompletion().invoke(() -> inFlight.remove(inFlightKey, self.get()))
                        .onCancellation().invoke(() -> inFlight.remove(inFlightKey, self.get()))
        );

        self.set(shared);
        return shared;
    }

    private void cleanupExpiredEntries() {
        for (Map.Entry<String, ConnectorExtractorStreamDTO> entry : cache.entrySet()) {
            ConnectorExtractorStreamDTO dto = entry.getValue();
            if (dto == null || isExpired(dto)) {
                cache.remove(entry.getKey());
            }
        }
    }

    private String buildInFlightKey(String keycloakId, String hash) {
        return keycloakId + ":" + hash;
    }

    private String hashRequest(AppBasedExtractorRequestDTO request) {
        try {
            String canonical = canonicalObjectMapper().writeValueAsString(request);
            return DigestUtils.sha256Hex(canonical);
        } catch (Exception e) {
            throw new IllegalStateException("Could not hash AppBasedExtractorRequestDTO", e);
        }
    }

    private ObjectMapper canonicalObjectMapper() {
        return JsonMapper.builder()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .build();
    }

    private boolean isExpired(ConnectorExtractorStreamDTO dto) {
        if (dto == null) {
            return true;
        }

        Date createdAt = dto.getCreatedAt();
        if (createdAt == null) {
            return true;
        }

        long createdAtMillis = createdAt.getTime();
        long nowMillis = Instant.now().toEpochMilli();

        return createdAtMillis + CACHE_TTL_MILLIS < nowMillis;
    }
}

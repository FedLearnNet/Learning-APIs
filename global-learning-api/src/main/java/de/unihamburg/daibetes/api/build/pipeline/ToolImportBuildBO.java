package de.unihamburg.daibetes.api.build.pipeline;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.api.pipeline.PipelineStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.app.ToolExternalHandler;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionAO;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.config.FLNetConfig;
import de.unihamburg.daibetes.config.ToolImportBuildingOnStart;
import de.unihamburg.daibetes.helper.PathOrUrl;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.context.api.ManagedExecutorConfig;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.spi.ObserverMethod;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.ws.rs.core.UriBuilder;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.eclipse.microprofile.context.ThreadContext;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ToolImportBuildBO {

    private static final String TOOL_IMPORT_BUILD_CHANNEL = "tool-import-builds";
    private static final Duration BUILD_TIMEOUT = Duration.ofHours(1);
    private static final Pattern AUTH_PARAMETER = Pattern.compile("(\\w+)=\"([^\"]*)\"");
    private static final String MANIFEST_TYPES = "application/vnd.oci.image.manifest.v1+json, "
            + "application/vnd.oci.image.index.v1+json, "
            + "application/vnd.docker.distribution.manifest.v2+json, "
            + "application/vnd.docker.distribution.manifest.list.v2+json";

    @Inject
    FLNetConfig config;

    @Inject
    ToolExternalHandler toolExternalHandler;

    @Inject
    FederatedAppVersionAO appVersionAO;

    @Inject
    FederatedAppAuthorAO authorAO;

    @Inject
    PipelineAO pipelineAO;

    @Inject
    PipelineBO pipelineBO;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    @ManagedExecutorConfig(cleared = ThreadContext.TRANSACTION)
    ManagedExecutor executor;

    @Inject
    @Channel(TOOL_IMPORT_BUILD_CHANNEL)
    @OnOverflow(value = OnOverflow.Strategy.BUFFER, bufferSize = 256)
    MutinyEmitter<Long> buildEmitter;

    private final Set<Long> queuedVersions = ConcurrentHashMap.newKeySet();
    private final Set<ImportedVersion> importedVersions = new LinkedHashSet<>();
    private final HttpClient registryClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private Cancellable importQueue;

    @PreDestroy
    void shutdown() {
        if (importQueue != null) {
            importQueue.cancel();
        }
        registryClient.shutdownNow();
    }

    // The existing Startup observer imports tools at the default observer priority.
    // Capture store existence before that import, and enqueue only after its transaction commits.
    public void prepareImports(@Observes @Priority(ObserverMethod.DEFAULT_PRIORITY - 1) StartupEvent event) {
        importedVersions.clear();
        if (!config.toolImports().enabled() || config.toolImports().buildOnImport() == ToolImportBuildingOnStart.NEVER) {
            return;
        }
        if (config.pipelineBuilderImage().filter(StringUtils::isNotBlank).isEmpty()) {
            Log.warnf("Skipping imported tool builds: pipeline builder image is not configured");
            return;
        }
        try {
            List<PathOrUrl> sources = PathOrUrl.filterByWhitelist(config.toolImports().paths().orElse(List.of()).stream()
                    .flatMap(toolExternalHandler::resolveJsonSources)
                    .sorted(Comparator.comparing(PathOrUrl::sortKey))
                    .toList(), config.toolImports().whiteList());
            List<FederatedAppDetailDTO> tools = new ArrayList<>(toolExternalHandler.loadAllApps(sources));
            toolExternalHandler.loadAllModels(sources).stream()
                    .filter(model -> model.getFederatedApp() != null)
                    .forEach(model -> tools.add(model.getFederatedApp()));
            QuarkusTransaction.requiringNew().run(() -> {
                for (FederatedAppDetailDTO tool : tools) {
                    if (tool.getUniqueAppId() == null || tool.getVersions() == null
                            || tool.getName() == null || tool.getSlug() == null || tool.getType() == null) {
                        continue;
                    }
                    for (var version : tool.getVersions()) {
                        if (version.getVersionPublishStatus() == null || version.getVersionPublishStatus() == PublishStatus.UNPUBLISHED
                                || StringUtils.isBlank(version.getPublishHash())) {
                            continue;
                        }
                        if (config.toolImports().buildOnImport() == ToolImportBuildingOnStart.IF_NOT_EXISTS_IN_STORE
                                && appVersionAO.existsByPublishHash(version.getPublishHash(), tool.getUniqueAppId())) {
                            continue;
                        }
                        importedVersions.add(new ImportedVersion(tool.getUniqueAppId(), version.getPublishHash()));
                    }
                }
            });
        } catch (Exception e) {
            importedVersions.clear();
            Log.warnf(e, "Failed to prepare imported tool builds");
        }
    }

    public void queueImports(@Observes @Priority(ObserverMethod.DEFAULT_PRIORITY + 1) StartupEvent event) {
        List<ImportedVersion> versions = List.copyOf(importedVersions);
        importedVersions.clear();
        if (versions.isEmpty()) {
            return;
        }
        // Wait for acknowledgement before sending the next version, so large imports cannot overflow the queue.
        importQueue = Multi.createFrom().iterable(versions)
                .onItem().transformToUniAndConcatenate(version -> Uni.createFrom().item(() ->
                                QuarkusTransaction.requiringNew().call(() -> appVersionAO
                                        .findByPublishHash(version.publishHash(), version.uniqueAppId())
                                        .map(FederatedAppVersionEntity::getId).orElse(null)))
                        .runSubscriptionOn(executor)
                        .onItem().transformToUni(id -> id == null ? Uni.createFrom().voidItem() : enqueue(id))
                        .onFailure().invoke(e -> Log.warnf(e, "Failed to queue imported tool version %s", version.publishHash()))
                        .onFailure().recoverWithNull())
                .subscribe().with(ignored -> { }, e -> Log.error("Imported tool build queue failed", e));
    }

    public Uni<Void> enqueue(Long appVersionId) {
        return Uni.createFrom().deferred(() -> {
            if (appVersionId == null || !queuedVersions.add(appVersionId)) {
                return Uni.createFrom().voidItem();
            }
            Log.infof("Queuing imported tool version %d for build", appVersionId);
            return Uni.createFrom().deferred(() -> buildEmitter.send(appVersionId))
                    .onFailure().invoke(e -> queuedVersions.remove(appVersionId));
        });
    }

    @Incoming(TOOL_IMPORT_BUILD_CHANNEL)
    public Uni<Void> build(Long appVersionId) {
        // Messaging processes this channel in order; acknowledgement waits for the actual build to finish.
        return Uni.createFrom().item(() -> prepareBuild(appVersionId))
                .runSubscriptionOn(executor)
                .onItem().transformToUni(run -> {
                    if (run == null) {
                        return Uni.createFrom().voidItem();
                    }
                    return Uni.createFrom().item(() -> QuarkusTransaction.requiringNew()
                                    .call(() -> pipelineBO.start(run.pipelineId(), run.keycloakId())))
                            .runSubscriptionOn(executor)
                            .onItem().transformToUni(ignored -> awaitBuild(run))
                            .onFailure().call(e -> Uni.createFrom().item(() -> {
                                        failBuild(run);
                                        return true;
                                    }).runSubscriptionOn(executor));
                })
                .onFailure().invoke(e -> Log.warnf(e, "Failed to build imported tool version %d", appVersionId))
                .onFailure().recoverWithNull()
                .onTermination().invoke(() -> queuedVersions.remove(appVersionId));
    }

    private BuildRun prepareBuild(Long appVersionId) {
        if (config.toolImports().buildOnImport() == ToolImportBuildingOnStart.NEVER) {
            return null;
        }
        String imageName = QuarkusTransaction.requiringNew().call(() -> appVersionAO
                .findByIdOptional(appVersionId).map(FederatedAppVersionEntity::getImageName).orElse(null));
        if (config.toolImports().buildOnImport() == ToolImportBuildingOnStart.IF_NOT_EXISTS_IN_REGISTRY
                && StringUtils.isNotBlank(imageName) && imageExistsInRegistry(imageName)) {
            Log.infof("Skipping imported tool version %d: image exists in registry", appVersionId);
            return null;
        }
        return QuarkusTransaction.requiringNew().call(() -> {
            var version = appVersionAO.findByIdOptional(appVersionId, LockModeType.PESSIMISTIC_WRITE);
            if (version.isEmpty()) {
                return null;
            }
            if (StringUtils.isBlank(version.get().getFederatedApp().getSourceUrl())) {
                Log.warnf("Skipping imported tool version %d: no source URL", appVersionId);
                return null;
            }
            if (pipelineAO.count("appVersion.id = ?1 and pipelineStatus in ?2", appVersionId,
                    List.of(PipelineStatus.PENDING, PipelineStatus.RUNNING)) > 0) {
                Log.infof("Skipping imported tool version %d: a build is already pending or running", appVersionId);
                return null;
            }
            String keycloakId = authorAO.getAllForApp(version.get().getFederatedApp().getId()).stream()
                    .map(author -> author.getKeycloakId())
                    .filter(StringUtils::isNotBlank)
                    .sorted().findFirst().orElse(null);
            if (keycloakId == null) {
                Log.warnf("Skipping imported tool version %d: no author available for pipeline authorization", appVersionId);
                return null;
            }
            PipelineCreateDTO dto = new PipelineCreateDTO();
            dto.setPipelineType(PipelineType.APP);
            dto.setAppVersionId(appVersionId);
            // Persist the pipeline before starting its container, so callbacks can find it immediately.
            PipelineDTO pipeline = pipelineBO.create(dto, keycloakId);
            return new BuildRun(pipeline.getId(), keycloakId);
        });
    }

    private Uni<Void> awaitBuild(BuildRun run) {
        return Uni.createFrom().item(() -> QuarkusTransaction.requiringNew()
                        .call(() -> pipelineBO.getById(run.pipelineId(), run.keycloakId()).getPipelineStatus()))
                .runSubscriptionOn(executor)
                .repeat().withDelay(Duration.ofSeconds(2)).indefinitely()
                .select().where(status -> status != PipelineStatus.PENDING && status != PipelineStatus.RUNNING)
                .select().first().toUni()
                .ifNoItem().after(BUILD_TIMEOUT).fail()
                .onItem().invoke(status -> Log.infof("Imported tool build pipeline %d finished with status %s", run.pipelineId(), status))
                .replaceWithVoid();
    }

    private void failBuild(BuildRun run) {
        try {
            pipelineBO.stopPipeline(run.pipelineId(), run.keycloakId());
            pipelineBO.setPipelineStatusTransactional(run.pipelineId(), run.keycloakId(), PipelineStatus.FAILED);
        } catch (Exception e) {
            Log.warnf(e, "Failed to clean up imported tool build pipeline %d", run.pipelineId());
        }
    }

    private boolean imageExistsInRegistry(String imageName) {
        try {
            URI manifest = manifestUri(imageName);
            HttpResponse<String> response = registryClient.send(manifestRequest(manifest, null), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 401) {
                String token = registryToken(response.headers().firstValue("WWW-Authenticate").orElse(""));
                response = registryClient.send(manifestRequest(manifest, token), HttpResponse.BodyHandlers.ofString());
            }
            if (response.statusCode() == 200) {
                return true;
            }
            if (response.statusCode() == 404) {
                return false;
            }
            throw new IllegalStateException("Registry returned HTTP " + response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Registry check interrupted for " + imageName, e);
        } catch (Exception e) {
            // An unavailable or unauthorized registry does not establish that an image is missing.
            throw new IllegalStateException("Could not verify registry image " + imageName, e);
        }
    }

    private HttpRequest manifestRequest(URI manifest, String token) {
        HttpRequest.Builder request = HttpRequest.newBuilder(manifest)
                .timeout(Duration.ofSeconds(10))
                .header("Accept", MANIFEST_TYPES)
                .method("HEAD", HttpRequest.BodyPublishers.noBody());
        if (StringUtils.isNotBlank(token)) {
            request.header("Authorization", "Bearer " + token);
        }
        return request.build();
    }

    private String registryToken(String challenge) throws Exception {
        if (!challenge.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new IllegalStateException("Registry requires unsupported authentication");
        }
        URI realm = null;
        String service = null;
        String scope = null;
        Matcher matcher = AUTH_PARAMETER.matcher(challenge);
        while (matcher.find()) {
            switch (matcher.group(1)) {
                case "realm" -> realm = URI.create(matcher.group(2));
                case "service" -> service = matcher.group(2);
                case "scope" -> scope = matcher.group(2);
            }
        }
        if (realm == null || !"https".equalsIgnoreCase(realm.getScheme())) {
            throw new IllegalStateException("Registry did not provide an HTTPS token endpoint");
        }
        UriBuilder tokenUri = UriBuilder.fromUri(realm);
        if (service != null) {
            tokenUri.queryParam("service", service);
        }
        if (scope != null) {
            tokenUri.queryParam("scope", scope);
        }
        HttpResponse<String> response = registryClient.send(HttpRequest.newBuilder(tokenUri.build())
                .timeout(Duration.ofSeconds(10)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Registry token endpoint returned HTTP " + response.statusCode());
        }
        JsonNode body = objectMapper.readTree(response.body());
        String token = body.path("token").asText(body.path("access_token").asText(null));
        if (StringUtils.isBlank(token)) {
            throw new IllegalStateException("Registry token endpoint returned no token");
        }
        return token;
    }

    private URI manifestUri(String imageName) {
        String repository = imageName.trim();
        String registry = "registry-1.docker.io";
        int slash = repository.indexOf('/');
        if (slash > 0) {
            String first = repository.substring(0, slash);
            if (first.contains(".") || first.contains(":") || first.equals("localhost")) {
                registry = first;
                repository = repository.substring(slash + 1);
            }
        }
        if (registry.equals("docker.io") || registry.equals("index.docker.io")) {
            registry = "registry-1.docker.io";
        }
        String reference = "latest";
        int digest = repository.indexOf('@');
        int tag = repository.lastIndexOf(':');
        if (digest >= 0) {
            reference = repository.substring(digest + 1);
            repository = repository.substring(0, digest);
            int tagged = repository.lastIndexOf(':');
            if (tagged > repository.lastIndexOf('/')) {
                repository = repository.substring(0, tagged);
            }
        } else if (tag > repository.lastIndexOf('/')) {
            reference = repository.substring(tag + 1);
            repository = repository.substring(0, tag);
        }
        if (registry.equals("registry-1.docker.io") && !repository.contains("/")) {
            repository = "library/" + repository;
        }
        return URI.create("https://" + registry + "/v2/" + repository + "/manifests/" + reference);
    }

    private record ImportedVersion(UUID uniqueAppId, String publishHash) {
    }

    private record BuildRun(Long pipelineId, String keycloakId) {
    }
}

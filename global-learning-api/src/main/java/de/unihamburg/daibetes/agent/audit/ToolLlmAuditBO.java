package de.unihamburg.daibetes.agent.audit;

import bio.cosy.feddb.core.api.app.AppPublishInfoDTO;
import bio.cosy.feddb.core.api.app.PublishStatus;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.agent.audit.pojo.AuditSeverity;
import de.unihamburg.daibetes.agent.audit.pojo.AuditVerdict;
import de.unihamburg.daibetes.agent.audit.pojo.ToolAuditFinding;
import de.unihamburg.daibetes.agent.audit.pojo.ToolAuditReport;
import de.unihamburg.daibetes.agent.audit.pojo.ToolLlmAuditCaller;
import de.unihamburg.daibetes.agent.audit.pojo.ToolLlmAuditInput;
import de.unihamburg.daibetes.agent.audit.pojo.ToolLlmAuditRequest;
import de.unihamburg.daibetes.agent.audit.pojo.ToolLlmAuditSourceSection;
import de.unihamburg.daibetes.agent.audit.source.ToolSourceArchiveService;
import de.unihamburg.daibetes.agent.audit.source.ToolSourceSnapshot;
import de.unihamburg.daibetes.api.app.FederatedAppEntity;
import de.unihamburg.daibetes.api.app.author.FederatedAppAuthorBO;
import de.unihamburg.daibetes.api.app.config.hyperparam.FederatedAppHyperParamConfigEntity;
import de.unihamburg.daibetes.api.app.config.input.FederatedAppInputConfigEntity;
import de.unihamburg.daibetes.api.app.config.output.FederatedAppOutputConfigEntity;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.config.FLNetConfig;
import io.quarkus.logging.Log;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Pattern;

@ApplicationScoped
public class ToolLlmAuditBO extends BaseBo<ToolLlmAuditDTO, ToolLlmAuditEntity, ToolLlmAuditAO, ToolLlmAuditMapper> {

    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    private static final int MAX_FINDINGS = 30;

    @Inject
    ToolLlmAuditLimitBO limitBO;

    @Inject
    ToolLlmAuditContextBO contextBO;

    @Inject
    ToolSourceArchiveService sourceArchiveService;

    @Inject
    ToolAuditorBot auditorBot;

    @Inject
    FederatedAppAuthorBO appAuthorBO;

    @Inject
    FLNetConfig config;

    // Global chat model, shown with the result and part of the fingerprint
    @ConfigProperty(name = "quarkus.langchain4j.openai.chat-model.model-name", defaultValue = "unknown")
    String modelName;

    private ExecutorService executor;

    @PostConstruct
    void init() {
        AtomicInteger threads = new AtomicInteger();
        executor = Executors.newFixedThreadPool(Math.max(1, config.llmAudit().parallelism()), runnable -> {
            Thread thread = new Thread(runnable, "llm-audit-" + threads.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    public ToolLlmAuditDTO request(Long appVersionId, ToolLlmAuditCaller caller, boolean force) {
        ToolLlmAuditRequest request = createAuditTransactional(appVersionId, caller, force);
        if (request.isCreated()) {
            Long auditId = request.getAudit().getId();
            executor.submit(() -> run(auditId));
        }
        return request.getAudit();
    }

    @Transactional
    public ToolLlmAuditRequest createAuditTransactional(Long appVersionId, ToolLlmAuditCaller caller, boolean force) {
        FederatedAppVersionEntity version = ao.lockVersion(appVersionId);
        if (version == null) {
            throw new NotFoundException("Tool version " + appVersionId + " not found");
        }
        checkAccess(version, caller);
        Optional<ToolLlmAuditEntity> active = ao.findActiveByVersionIdOptional(appVersionId);
        if (active.isPresent()) {
            return new ToolLlmAuditRequest(mapper.entityToDto(active.get()), false);
        }
        if (force && !caller.isAdmin()) {
            throw new ForbiddenException("Only admins can force a new LLM audit");
        }
        String fingerprint = contextBO.fingerprintOf(inputOf(null, version), modelName);
        if (!force) {
            Optional<ToolLlmAuditEntity> finished = ao.findFinishedByFingerprintOptional(appVersionId, fingerprint);
            if (finished.isPresent()) {
                return new ToolLlmAuditRequest(mapper.entityToDto(finished.get()), false);
            }
        }
        limitBO.checkRequestAllowed(caller.getKeycloakId(), caller.isAdmin());

        ToolLlmAuditEntity entity = new ToolLlmAuditEntity();
        entity.setKeycloakId(caller.getKeycloakId());
        entity.setAppVersion(version);
        entity.setStatus(ToolLlmAuditStatus.PENDING);
        entity.setFingerprint(fingerprint);
        entity.setCommitHash(commitHashOf(version));
        entity.setModelName(modelName);
        ao.persist(entity);
        Log.infof("LLM audit %d requested for tool version %d", entity.getId(), appVersionId);
        return new ToolLlmAuditRequest(mapper.entityToDto(entity), true);
    }

    @ActivateRequestContext
    void run(Long auditId) {
        ToolLlmAuditInput input;
        try {
            input = startAuditTransactional(auditId);
        } catch (Exception e) {
            Log.errorf(e, "Cannot start LLM audit %d", auditId);
            failAuditTransactional(auditId, "Audit could not be started");
            return;
        }
        try {
            String ref = input.getCommitHash() != null && COMMIT.matcher(input.getCommitHash()).matches()
                    ? input.getCommitHash() : "main";
            ToolSourceSnapshot snapshot = sourceArchiveService.download(input.getSourceUrl(), ref).orElse(null);
            AppPublishInfoDTO info = input.getPublishInfo();
            ToolLlmAuditSourceSection source = contextBO.sourceOf(snapshot, info);
            Log.infof("LLM audit %d: %d of %d files, %d characters of source", auditId, source.getReviewedFiles(),
                    source.getTotalFiles(), source.getText().length());
            ToolAuditReport report = auditorBot.audit(contextBO.metadataOf(input), contextBO.sastOf(info),
                    contextBO.vulnerabilitiesOf(info), contextBO.malwareOf(info), source.getText());
            finishAuditTransactional(auditId, report, source, info);
            Log.infof("LLM audit %d finished", auditId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            failAuditTransactional(auditId, "Audit was interrupted");
        } catch (Exception e) {
            Log.errorf(e, "LLM audit %d failed", auditId);
            failAuditTransactional(auditId, "The auditor model did not return a valid report: "
                    + StringUtils.abbreviate(StringUtils.defaultString(e.getMessage()), 300));
        }
    }

    @Transactional
    public ToolLlmAuditInput startAuditTransactional(Long auditId) {
        ToolLlmAuditEntity entity = ao.findByIdOptional(auditId)
                .orElseThrow(() -> new NotFoundException("LLM audit " + auditId + " not found"));
        entity.setStatus(ToolLlmAuditStatus.RUNNING);
        entity.setStartedAt(new Date());
        return inputOf(auditId, entity.getAppVersion());
    }

    @Transactional
    public void finishAuditTransactional(Long auditId, ToolAuditReport report, ToolLlmAuditSourceSection source,
                                         AppPublishInfoDTO info) {
        ToolLlmAuditEntity entity = ao.findById(auditId);
        if (entity == null) {
            return;
        }
        if (report == null || report.getVerdict() == null) {
            fail(entity, "The auditor model returned no verdict");
            return;
        }
        sanitize(report, source, info);
        entity.setReport(report);
        entity.setVerdict(report.getVerdict());
        entity.setRiskLevel(report.getRiskLevel());
        entity.setSourceAvailable(source.isAvailable());
        entity.setSourceTruncated(source.isTruncated());
        entity.setReviewedFiles(source.getReviewedFiles());
        entity.setTotalFiles(source.getTotalFiles());
        entity.setStatus(ToolLlmAuditStatus.FINISHED);
        entity.setFinishedAt(new Date());
    }

    @Transactional
    public void failAuditTransactional(Long auditId, String message) {
        ToolLlmAuditEntity entity = ao.findById(auditId);
        if (entity != null) {
            fail(entity, message);
        }
    }

    @Transactional
    public int failInterruptedAuditsTransactional() {
        return ao.failActive("Interrupted by a restart");
    }

    @Transactional
    public List<ToolLlmAuditDTO> listByVersionTransactional(Long appVersionId, ToolLlmAuditCaller caller) {
        checkAccess(findVersion(appVersionId), caller);
        return mapper.entitiesToDtos(ao.listByVersionId(appVersionId));
    }

    @Transactional
    public Optional<ToolLlmAuditDTO> findLatestByVersionTransactional(Long appVersionId, ToolLlmAuditCaller caller) {
        checkAccess(findVersion(appVersionId), caller);
        return ao.findLatestByVersionIdOptional(appVersionId).map(mapper::entityToDto);
    }

    @Transactional
    public ToolLlmAuditDTO findByIdTransactional(Long id, ToolLlmAuditCaller caller) {
        ToolLlmAuditEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("LLM audit " + id + " not found"));
        checkAccess(entity.getAppVersion(), caller);
        return mapper.entityToDto(entity);
    }

    private FederatedAppVersionEntity findVersion(Long appVersionId) {
        FederatedAppVersionEntity version = ao.getEntityManager().find(FederatedAppVersionEntity.class, appVersionId);
        if (version == null) {
            throw new NotFoundException("Tool version " + appVersionId + " not found");
        }
        return version;
    }

    // Unpublished versions stay with their authors, admins and auditors
    private void checkAccess(FederatedAppVersionEntity version, ToolLlmAuditCaller caller) {
        if (version.getVersionPublishStatus() == PublishStatus.PUBLISHED || caller.isAdmin() || caller.isAuditor()) {
            return;
        }
        if (!appAuthorBO.isUserAuthor(caller.getKeycloakId(), version.getFederatedApp().getId())) {
            throw new ForbiddenException();
        }
    }

    private void fail(ToolLlmAuditEntity entity, String message) {
        entity.setStatus(ToolLlmAuditStatus.FAILED);
        entity.setErrorMessage(message);
        entity.setFinishedAt(new Date());
    }

    // Enforces the hard verdict rules independent of the model
    private void sanitize(ToolAuditReport report, ToolLlmAuditSourceSection source, AppPublishInfoDTO info) {
        List<ToolAuditFinding> findings = new ArrayList<>(Objects.requireNonNullElse(report.getFindings(), List.of()));
        findings.removeIf(Objects::isNull);
        findings.sort(Comparator.comparingInt(f -> f.getSeverity() == null ? AuditSeverity.INFO.ordinal()
                : f.getSeverity().ordinal()));
        report.setFindings(new ArrayList<>(findings.stream().limit(MAX_FINDINGS).toList()));
        report.setPositives(new ArrayList<>(Objects.requireNonNullElse(report.getPositives(), List.of())));
        report.setLimitations(new ArrayList<>(Objects.requireNonNullElse(report.getLimitations(), List.of())));

        AuditSeverity highest = findings.stream().map(ToolAuditFinding::getSeverity).filter(Objects::nonNull)
                .min(Comparator.naturalOrder()).orElse(AuditSeverity.INFO);
        if (report.getRiskLevel() == null || highest.ordinal() < report.getRiskLevel().ordinal()) {
            report.setRiskLevel(highest);
        }
        if (highest == AuditSeverity.CRITICAL && report.getVerdict() != AuditVerdict.INCONCLUSIVE) {
            report.setVerdict(AuditVerdict.UNSAFE);
        }
        boolean malware = info != null && info.getMalwareScanResult() != null
                && info.getMalwareScanResult().getInfectedCount() > 0;
        if (malware && report.getVerdict() == AuditVerdict.SAFE) {
            report.setVerdict(AuditVerdict.SAFE_WITH_CONCERNS);
            report.getLimitations().add("The malware scan reported infected files; the verdict was lowered.");
        }
        if (!source.isAvailable()) {
            if (report.getVerdict() == AuditVerdict.SAFE || report.getVerdict() == AuditVerdict.SAFE_WITH_CONCERNS) {
                report.setVerdict(AuditVerdict.INCONCLUSIVE);
            }
            report.getLimitations().add("The source code could not be downloaded; only scan results were reviewed.");
        } else if (source.isTruncated()) {
            report.getLimitations().add(String.format("Only %d of %d files were reviewed in full due to size limits.",
                    source.getReviewedFiles(), source.getTotalFiles()));
        }
    }

    private String commitHashOf(FederatedAppVersionEntity version) {
        return version.getPublishInfo() == null ? null : version.getPublishInfo().getCommitHash();
    }

    private ToolLlmAuditInput inputOf(Long auditId, FederatedAppVersionEntity version) {
        FederatedAppEntity app = version.getFederatedApp();
        List<String> inputs = configLines(version.getInputConfig(), FederatedAppInputConfigEntity::getName,
                c -> String.valueOf(c.getType()), FederatedAppInputConfigEntity::getDescription);
        List<String> outputs = configLines(version.getOutputConfig(), FederatedAppOutputConfigEntity::getName,
                c -> String.valueOf(c.getType()), FederatedAppOutputConfigEntity::getDescription);
        List<String> hyperParams = configLines(version.getHyperParamConfig(), FederatedAppHyperParamConfigEntity::getName,
                c -> String.valueOf(c.getType()), FederatedAppHyperParamConfigEntity::getDescription);
        return new ToolLlmAuditInput(auditId, app.getName(), String.valueOf(app.getType()),
                version.getVersionFormatted(), version.getShortDescription(), app.getSourceUrl(),
                commitHashOf(version), Boolean.TRUE.equals(version.getNeedsInternetAccess()),
                Boolean.TRUE.equals(version.getNeedsHostAccess()), app.isSupportsFederatedLearning(),
                inputs, outputs, hyperParams, version.getPublishInfo());
    }

    private <T> List<String> configLines(Collection<T> configs, Function<T, String> name,
                                         Function<T, String> type,
                                         Function<T, String> description) {
        if (configs == null) {
            return List.of();
        }
        return configs.stream()
                .map(c -> name.apply(c) + " (" + type.apply(c) + ")"
                        + (StringUtils.isBlank(description.apply(c)) ? ""
                        : ": " + StringUtils.abbreviate(StringUtils.normalizeSpace(description.apply(c)), 200)))
                .sorted()
                .toList();
    }
}

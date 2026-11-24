package de.unihamburg.daibetes.agent.audit;

import de.unihamburg.daibetes.config.FLNetConfig;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ServiceUnavailableException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

// Sliding 24 hour quota per user plus a platform-wide queue cap
@ApplicationScoped
public class ToolLlmAuditLimitBO {

    static final Duration WINDOW = Duration.ofHours(24);

    @Inject
    ToolLlmAuditAO ao;

    @Inject
    FLNetConfig config;

    public ToolLlmAuditQuotaDTO quotaOf(String keycloakId, boolean unlimited) {
        int limit = config.llmAudit().perUserPerDay();
        List<Date> requests = ao.listRequestTimesSince(keycloakId, Date.from(Instant.now().minus(WINDOW)));
        int used = requests.size();
        Date nextSlotAt = requests.isEmpty() ? null : Date.from(requests.getFirst().toInstant().plus(WINDOW));
        int remaining = unlimited ? Integer.MAX_VALUE : Math.max(0, limit - used);
        return new ToolLlmAuditQuotaDTO(config.llmAudit().enabled(), unlimited, limit, used, remaining, nextSlotAt);
    }

    public void checkRequestAllowed(String keycloakId, boolean unlimited) {
        if (!config.llmAudit().enabled()) {
            throw new ServiceUnavailableException("LLM audits are disabled");
        }
        if (ao.countActive() >= config.llmAudit().maxQueued()) {
            throw tooManyRequests("Too many LLM audits are queued, try again later");
        }
        if (unlimited) {
            return;
        }
        ToolLlmAuditQuotaDTO quota = quotaOf(keycloakId, false);
        if (quota.getRemaining() <= 0) {
            throw tooManyRequests(String.format("Limit of %d LLM audits per 24 hours reached, next one possible at %s",
                    quota.getLimit(), quota.getNextSlotAt().toInstant()));
        }
    }

    private WebApplicationException tooManyRequests(String message) {
        return new WebApplicationException(message, Response.Status.TOO_MANY_REQUESTS);
    }
}

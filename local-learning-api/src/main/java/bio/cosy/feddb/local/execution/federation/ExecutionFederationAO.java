package bio.cosy.feddb.local.execution.federation;

import bio.cosy.feddb.core.execution.run.types.RunStatusTypes;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ExecutionFederationAO implements PanacheRepository<ExecutionFederationEntity> {

    public List<ExecutionFederationEntity> listPendingRelayCerts() {
        return list("""
                        relayCsr is not null
                        and (relayCertSigned is null or relayCertSigned = false)
                        and (relayStopped is null or relayStopped = false)
                        and step.status not in ?1
                        """,
                RunStatusTypes.terminalStates());
    }

    public List<ExecutionFederationEntity> listOpenControllerRuns(Long runId) {
        return list("step.run.id = ?1 and relayCsr is not null and (relayStopped is null or relayStopped = false)", runId);
    }
}

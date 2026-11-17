package de.unihamburg.daibetes.execution.federation;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExecutionParticipantAO implements PanacheRepository<ExecutionParticipantEntity> {
}

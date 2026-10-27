package de.unihamburg.daibetes.execution.experiment;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExecutionExperimentAO implements PanacheRepository<ExecutionExperimentEntity> {
}

package de.unihamburg.daibetes.api.build.pipeline.steps;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PipelineStepAO implements PanacheRepository<PipelineStepEntity> {

}

package de.unihamburg.daibetes.api.project.experiment.local.step;

import bio.cosy.feddb.core.api.workflow.base.step.BaseWorkflowStepAO;
import de.unihamburg.daibetes.api.project.experiment.local.ProjectLocalExperimentEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class ProjectLocalExperimentStepAO extends BaseWorkflowStepAO<ProjectLocalExperimentStepEntity> {


}

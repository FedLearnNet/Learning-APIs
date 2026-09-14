package de.unihamburg.daibetes.api.project;

import bio.cosy.feddb.core.api.app.config.TabularSchemaDTO;
import bio.cosy.feddb.core.api.project.ProjectDTO;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;

@ApplicationScoped
public class ProjectServiceImpl implements ProjectService {


    @Inject
    ProjectBO projectBO;

    @Inject
    UserIdentity userIdentity;

    @Override
    public List<ProjectDTO> list() {
        String keycloakId = userIdentity.getKeycloakId();
        return projectBO.getAll(keycloakId);
    }

    @Override
    @Transactional
    public Response create(ProjectCreateDTO createDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        ProjectDTO createdProject = projectBO.create(createDTO, keycloakId);
        return Response.status(Response.Status.CREATED).entity(createdProject).build();
    }

    @Override
    public ProjectDetailDTO retrieve(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return projectBO.get(id, keycloakId);
    }

    @Override
    @Transactional
    public ProjectDetailDTO update(Long id, ProjectDetailDTO projectDTO) {
        String keycloakId = userIdentity.getKeycloakId();
        return projectBO.update(id, projectDTO, keycloakId);
    }

    @Override
    @Transactional
    public TabularSchemaDTO createExportConfigSchema(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return projectBO.createExportConfigSchema(id, keycloakId);
    }

    @Override
    @Transactional
    public Response generateExportConfigTestCsv(Long id, Integer amount) {
        String keycloakId = userIdentity.getKeycloakId();
        return projectBO.generateExportConfigTestCsv(id, amount, keycloakId);
    }

    @Override
    @Transactional
    public Response delete(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        projectBO.delete(id, keycloakId);
        return Response.ok("Project deleted successfully").build();
    }

    @Override
    @Transactional
    public Response upload(Long id, FileUpload file) {
        String keycloakId = userIdentity.getKeycloakId();
        return Response.status(Response.Status.CREATED)
                .entity(projectBO.storeFile(id, file, keycloakId))
                .build();
    }

}

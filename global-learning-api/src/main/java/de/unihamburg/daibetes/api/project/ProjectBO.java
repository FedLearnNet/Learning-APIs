package de.unihamburg.daibetes.api.project;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.app.config.TabularSchemaDTO;
import bio.cosy.feddb.core.api.project.ProjectDTO;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.ProjectStatus;
import bio.cosy.feddb.core.base.BaseBo;
import de.unihamburg.daibetes.api.file.FileBO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.membership.ProjectMembershipAO;
import de.unihamburg.daibetes.api.project.membership.ProjectMembershipBO;
import de.unihamburg.daibetes.api.project.membership.ProjectMembershipDTO;
import de.unihamburg.daibetes.api.project.membership.ProjectMembershipEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ProjectBO extends BaseBo<ProjectDetailDTO, ProjectEntity, ProjectAO, ProjectMapper> {

    @Inject
    ProjectMembershipBO projectMembershipBO;

    @Inject
    ProjectMembershipAO projectMembershipAO;

    @Inject
    FileBO fileBO;

    @Inject
    ExportConfigUtilsBO exportConfigUtilsBO;

    public ProjectDTO create(ProjectCreateDTO dto, String keycloakId) {
        ProjectDTO created = create(mapper.createToDTO(dto));
        if (created == null) {
            throw new ClientErrorException("Project already exists", Response.Status.CONFLICT);
        }
        ProjectMembershipDTO membership = new ProjectMembershipDTO(keycloakId, created.getId());
        membership = projectMembershipBO.create(membership);
        if (membership == null) {
            throw new ClientErrorException("Project Membership already exists", Response.Status.CONFLICT);
        }
        return created;
    }

    public List<ProjectDTO> getAll(String keycloakId) {
        List<ProjectEntity> entities = ao.getAllByUser(keycloakId);
        return entities.stream().map(mapper::entityToSimpleDTO).toList();
    }

    public ProjectDetailDTO get(Long id, String keycloakId) {
        if (id == null) {
            throw new NotFoundException("project_id empty");
        }
        ProjectEntity projectEntity = ao.findById(id);
        if (projectEntity == null) {
            throw new NotFoundException("Project not found");
        }
        if (!projectMembershipAO.isUserIsMember(keycloakId, projectEntity)) {
            throw new NotAllowedException("User not a member of the project");
        }
        return mapper.entityToDto(projectEntity);
    }

    public boolean delete(Long pId, String keycloakId) {
        ProjectEntity projectEntity = ao.findById(pId);
        if (projectEntity == null) {
            throw new NotFoundException("Project not found");
        }
        List<ProjectStatus> statusList = projectEntity.getExperiments().stream()
                .map(ProjectFederatedExperimentEntity::getExperimentStatus).toList();

        if (statusList.contains(ProjectStatus.RUNNING) || statusList.contains(ProjectStatus.PREPARE)) {
            throw new NotAllowedException("Project is running");
        }
        Optional<ProjectMembershipEntity> projectMembership = projectMembershipAO.getMemberByUser(keycloakId, projectEntity.getId());
        if (projectMembership.isEmpty()) {
            throw new NotAllowedException("User is not a member of the project");
        }
        if (projectMembershipAO.getAllByUser(keycloakId).size() == 1) {
            deleteById(pId);
        } else {
            projectMembershipBO.deleteById(projectMembership.get().getId());
        }
        return true;
    }

    public ProjectDetailDTO update(Long id, ProjectDetailDTO projectDto, String keycloakId) {

        ProjectDetailDTO currentDto = get(id, keycloakId);

        Optional<ProjectMembershipEntity> projectMembership = projectMembershipAO.getByProjectAndUser(
                currentDto.getId(), keycloakId);

        if (projectMembership.isEmpty()) {
            throw new NotAllowedException("User is not a coordinator");
        }
        projectDto.setVersion(currentDto.getVersion());
        currentDto = update(projectDto);
        return currentDto;
    }

    public void setWorkflow(Long projectId, String keycloakId, WorkflowEntity workflow) {
        ProjectEntity projectEntity = ao.findById(projectId);
        if (projectEntity == null) {
            throw new NotFoundException("Project not found");
        }
        if (!projectMembershipAO.isUserIsMember(keycloakId, projectEntity)) {
            throw new NotAllowedException("User not a member of the project");
        }
        projectEntity.setWorkflow(workflow);
        projectEntity.setUpdatedAt(new Date());
        ao.persist(projectEntity);
    }


    public ProjectDetailDTO storeFile(Long id, FileUpload file, String keycloakId) {
        ProjectDetailDTO currentDto = get(id, keycloakId);

        Optional<ProjectMembershipEntity> projectMembership = projectMembershipAO.getByProjectAndUser(
                currentDto.getId(), keycloakId);

        if (projectMembership.isEmpty()) {
            throw new NotAllowedException("User is not a coordinator");
        }
        if (currentDto.getFile() != null) {
            fileBO.deleteById(currentDto.getFile().getId(), keycloakId);
        }
        String name = file.fileName();
        FileDTO fileDto = fileBO.create(file.uploadedFile().toFile(), name, keycloakId);
        currentDto.setFile(fileDto);
        currentDto = update(currentDto);
        currentDto.setFile(fileDto);
        return currentDto;
    }

    public TabularSchemaDTO createExportConfigSchema(Long id, String keycloakId) {
        ProjectDetailDTO currentDto = get(id, keycloakId);
        return exportConfigUtilsBO.createSchema(currentDto.getExportConfig());
    }

    public Response generateExportConfigTestCsv(Long id, Integer amount, String keycloakId) {
        ProjectDetailDTO currentDto = get(id, keycloakId);
        return exportConfigUtilsBO.generateTestCsv(currentDto.getExportConfig(), amount);
    }
}

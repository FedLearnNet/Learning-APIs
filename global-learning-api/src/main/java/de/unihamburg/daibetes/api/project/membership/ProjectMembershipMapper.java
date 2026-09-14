package de.unihamburg.daibetes.api.project.membership;

import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface ProjectMembershipMapper extends BaseMapper<ProjectMembershipDTO, ProjectMembershipEntity> {

    @Mapping(target = "projectId", source = "project.id")
    ProjectMembershipDTO entityToDto(ProjectMembershipEntity entity);

    @Mapping(target = "project.id", source = "projectId")
    ProjectMembershipEntity dtoToEntity(ProjectMembershipDTO sourceCode);
}

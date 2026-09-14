package bio.cosy.feddb.local.api.learning.project;

import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.query.QueryEntity;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

@Mapper(config = QuarkusMappingConfig.class)
public interface FederatedLearningProjectMapper extends BaseMapper<ProjectDetailDTO, FederatedLearningProjectEntity> {


    @Mappings({
            @Mapping(target = "status", ignore = true),
            @Mapping(target = "queryId", source = "query.id"),
            @Mapping(target = "audited", ignore = true),
            @Mapping(target = "role", ignore = true),
            @Mapping(target = "workflowId", source = "workflow.id"),
            @Mapping(target = "file", ignore = true),
            @Mapping(target = "globalUniqueQueryId", source = "query.globalQueryId")
    })
    ProjectDetailDTO entityToDto(FederatedLearningProjectEntity entity);

    @Mappings({
            @Mapping(target = "query", source = "queryId", qualifiedByName = "toQuery"),
            @Mapping(target = "request", ignore = true),
            @Mapping(target = "experiment", ignore = true),
            @Mapping(target = "workflow.id", source = "workflowId")
    })
    FederatedLearningProjectEntity dtoToEntity(ProjectDetailDTO sourceCode);


    @Named("toQuery")
    default QueryEntity toQuery(Long id) {
        if (id == null) {
            return null;
        }
        QueryEntity queryEntity = new QueryEntity();
        queryEntity.setId(id);
        return queryEntity;
    }

}

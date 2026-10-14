package de.unihamburg.daibetes.api.query;

import bio.cosy.feddb.core.api.query.EnhancedQueryItemDTO;
import bio.cosy.feddb.core.api.query.Lang2QueryDocuments;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryDetailDTO;
import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import org.mapstruct.AfterMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

import java.util.*;
import java.util.stream.Collectors;

@Mapper(config = QuarkusMappingConfig.class)
public interface QueryMapper extends BaseMapper<QueryDTO, QueryEntity> {


    @Mappings({
            @Mapping(target = "query", ignore = true),
            @Mapping(target = "projectIds", source = "projects", qualifiedByName = "projectEntityToIds"),
            @Mapping(target = "roles", ignore = true)
    })
    QueryDTO entityToDto(QueryEntity entity);

    @Mappings({
            @Mapping(target = "query", ignore = true),
            @Mapping(target = "projectIds", source = "projects", qualifiedByName = "projectEntityToIds"),
            @Mapping(target = "roles", ignore = true),
            @Mapping(target = "enhancedQuery", ignore = true),
            @Mapping(target = "olderQueries", ignore = true)
    })
    QueryDetailDTO entityToDetailDto(QueryEntity entity);

    @Mappings({
            @Mapping(target = "ontologyName", ignore = true),
            @Mapping(target = "dataTypeName", ignore = true)
    })
    EnhancedQueryItemDTO queryToEnhanced(QueryItemDTO queryItem);

    @Mappings({
            @Mapping(target = "groupId", source = "groupId"),
            @Mapping(target = "queryString", source = ".", qualifiedByName = "dtoToQueryString"),
            @Mapping(target = "projects", source = "projectIds", qualifiedByName = "queryDTOToProjectEntity")
    })
    QueryEntity dtoToEntity(QueryDTO dto);

    @Mappings({
            @Mapping(target = "groupId", ignore = true),
            @Mapping(target = "globalUniqueId", ignore = true),
            @Mapping(target = "hasFired", ignore = true),
            @Mapping(target = "hasResult", ignore = true),
            @Mapping(target = "latestDataStatisticsRequest", ignore = true),
            @Mapping(target = "latestDataStatisticsRequestTimestamp", ignore = true),
            @Mapping(target = "queryString", source = ".", qualifiedByName = "dtoToQueryString"),
            @Mapping(target = "projects", ignore = true),
            @Mapping(target = "result", ignore = true),
    })
    QueryEntity createDtoToEntity(QueryCreateDTO dto);

    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "projects", ignore = true),
            @Mapping(target = "globalUniqueId", ignore = true),
            @Mapping(target = "result", ignore = true),
            @Mapping(target = "hasResult", constant = "false"),
            @Mapping(target = "hasFired", constant = "false"),
            @Mapping(target = "latestDataStatisticsRequest", ignore = true),
            @Mapping(target = "latestDataStatisticsRequestTimestamp", ignore = true)
    })
    QueryEntity createVersionFromEntity(QueryEntity source);

    @Mappings({
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "projects", ignore = true),
            @Mapping(target = "groupId", source = "source.groupId"),
            @Mapping(target = "keycloakId", source = "source.keycloakId"),
            @Mapping(target = "globalUniqueId", ignore = true),
            @Mapping(target = "name", source = "request.name"),
            @Mapping(target = "description", source = "request.description"),
            @Mapping(target = "queryString", source = "request", qualifiedByName = "dtoToQueryString"),
            @Mapping(target = "result", ignore = true),
            @Mapping(target = "error", ignore = true),
            @Mapping(target = "hasResult", constant = "false"),
            @Mapping(target = "hasFired", constant = "false"),
            @Mapping(target = "latestDataStatisticsRequest", ignore = true),
            @Mapping(target = "latestDataStatisticsRequestTimestamp", ignore = true)
    })
    QueryEntity createVersionFromRequest(QueryEntity source, QueryDTO request);

    @AfterMapping
    default void readQueryDocument(QueryEntity entity, @MappingTarget QueryDTO dto) {
        Lang2QueryDocuments.readInto(entity.getQueryString(), dto);
    }

    @AfterMapping
    default void readQueryDocument(QueryEntity entity, @MappingTarget QueryDetailDTO dto) {
        Lang2QueryDocuments.readInto(entity.getQueryString(), dto);
    }

    @Named("dtoToQueryString")
    default String dtoToQueryString(QueryDTO dto) {
        return Lang2QueryDocuments.toQueryString(dto);
    }

    @Named("queryDTOToProjectEntity")
    default Set<ProjectEntity> queryDTOToProjectEntity(Set<Long> projectIds) {
        if (projectIds == null || projectIds.isEmpty()) {
            return null;
        }
        return projectIds.stream()
                .map(p -> {
                    ProjectEntity projectEntity = new ProjectEntity();
                    projectEntity.setId(p);
                    return projectEntity;
                })
                .collect(Collectors.toSet());
    }

    @Named("projectEntityToIds")
    default Set<Long> projectEntityToIds(Set<ProjectEntity> projects) {
        if (projects == null || projects.isEmpty()) {
            return new HashSet<>();
        }
        return projects.stream()
                .map(ProjectEntity::getId)
                .collect(Collectors.toSet());
    }
}

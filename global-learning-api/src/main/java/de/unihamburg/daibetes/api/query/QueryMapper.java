package de.unihamburg.daibetes.api.query;

import bio.cosy.feddb.core.api.query.EnhancedQueryItemDTO;
import bio.cosy.feddb.core.api.query.QueryDTO;
import bio.cosy.feddb.core.api.query.QueryDetailDTO;
import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.base.BaseMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.unihamburg.daibetes.api.project.ProjectEntity;
import de.unihamburg.daibetes.helper.QuarkusMappingConfig;
import io.quarkus.logging.Log;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import org.mapstruct.Named;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Mapper(config = QuarkusMappingConfig.class)
public interface QueryMapper extends BaseMapper<QueryDTO, QueryEntity> {

    ObjectMapper objectMapper = new ObjectMapper();


    @Mappings({
            @Mapping(target = "query", source = "queryString", qualifiedByName = "jsonStringToQueryItems"),
            @Mapping(target = "projectIds", source = "projects", qualifiedByName = "projectEntityToIds"),
            @Mapping(target = "roles", ignore = true)
    })
    QueryDTO entityToDto(QueryEntity entity);

    @Mappings({
            @Mapping(target = "query", source = "queryString", qualifiedByName = "jsonStringToQueryItems"),
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
            @Mapping(target = "queryString", source = "query", qualifiedByName = "queryItemsToJsonString"),
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
            @Mapping(target = "queryString", source = "query", qualifiedByName = "queryItemsToJsonString"),
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
            @Mapping(target = "queryString", source = "request.query", qualifiedByName = "queryItemsToJsonString"),
            @Mapping(target = "result", ignore = true),
            @Mapping(target = "error", ignore = true),
            @Mapping(target = "hasResult", constant = "false"),
            @Mapping(target = "hasFired", constant = "false"),
            @Mapping(target = "latestDataStatisticsRequest", ignore = true),
            @Mapping(target = "latestDataStatisticsRequestTimestamp", ignore = true)
    })
    QueryEntity createVersionFromRequest(QueryEntity source, QueryDTO request);

    @Named("jsonStringToQueryItems")
    default List<QueryItemDTO> jsonStringToQueryItems(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(jsonString, objectMapper.getTypeFactory().constructCollectionType(List.class,
                    QueryItemDTO.class));
        } catch (IOException e) {
            Log.error("Error parsing JSON string to QueryItemDTO list", e);
            return new ArrayList<>();
        }
    }

    @Named("queryItemsToJsonString")
    default String queryItemsToJsonString(List<QueryItemDTO> queryItems) {
        if (queryItems == null || queryItems.isEmpty()) {
            return "";
        }
        try {
            return objectMapper.writeValueAsString(queryItems);
        } catch (JsonProcessingException e) {
            Log.error("Error converting QueryItemDTO list to JSON string", e);
            return "";
        }
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

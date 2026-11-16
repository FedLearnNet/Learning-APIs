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

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Maps stored queries to API DTOs. The query tree is read from and written to {@code query_string}.
 */
@Mapper(config = QuarkusMappingConfig.class)
public interface QueryMapper extends BaseMapper<QueryDTO, QueryEntity> {


    /**
     * Maps a stored query to its API representation.
     *
     * @param entity stored query
     * @return the API query
     */
    @Mappings({
            @Mapping(target = "query", ignore = true),
            @Mapping(target = "projectIds", source = "projects", qualifiedByName = "projectEntityToIds"),
            @Mapping(target = "roles", ignore = true)
    })
    QueryDTO entityToDto(QueryEntity entity);

    /**
     * Maps a stored query to its detailed API representation.
     *
     * @param entity stored query
     * @return the detailed API query
     */
    @Mappings({
            @Mapping(target = "query", ignore = true),
            @Mapping(target = "projectIds", source = "projects", qualifiedByName = "projectEntityToIds"),
            @Mapping(target = "roles", ignore = true),
            @Mapping(target = "enhancedQuery", ignore = true),
            @Mapping(target = "olderQueries", ignore = true)
    })
    QueryDetailDTO entityToDetailDto(QueryEntity entity);

    /**
     * Maps one leaf to the enhanced shape used when ontology names are filled in later.
     *
     * @param queryItem leaf to enhance
     * @return the enhanced leaf
     */
    @Mappings({
            @Mapping(target = "ontologyName", ignore = true),
            @Mapping(target = "dataTypeName", ignore = true)
    })
    EnhancedQueryItemDTO queryToEnhanced(QueryItemDTO queryItem);

    /**
     * Maps an API query to a new stored row.
     *
     * @param dto query to store
     * @return the entity
     */
    @Mappings({
            @Mapping(target = "groupId", source = "groupId"),
            @Mapping(target = "queryString", source = ".", qualifiedByName = "dtoToQueryString"),
            @Mapping(target = "projects", source = "projectIds", qualifiedByName = "queryDTOToProjectEntity")
    })
    QueryEntity dtoToEntity(QueryDTO dto);

    /**
     * Maps a create request to a new stored row.
     *
     * @param dto create request
     * @return the entity
     */
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

    /**
     * Copies a stored query into a new unsaved version.
     *
     * @param source query to copy
     * @return the new version
     */
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

    /**
     * Copies a stored query and applies the fields from an update request.
     *
     * @param source query to copy
     * @param request replacement name, description, and query tree
     * @return the new version
     */
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

    /**
     * Copies the stored query document onto the mapped DTO.
     *
     * @param entity source query row
     * @param dto mapped query
     */
    @AfterMapping
    default void readQueryDocument(final QueryEntity entity, @MappingTarget final QueryDTO dto) {
        Lang2QueryDocuments.readInto(entity.getQueryString(), dto);
    }

    /**
     * Copies the stored query document onto the mapped detail DTO.
     *
     * @param entity source query row
     * @param dto mapped query detail
     */
    @AfterMapping
    default void readQueryDocument(final QueryEntity entity, @MappingTarget final QueryDetailDTO dto) {
        Lang2QueryDocuments.readInto(entity.getQueryString(), dto);
    }

    /**
     * Serializes the query tree for the {@code query_string} column.
     *
     * @param dto query to store
     * @return the JSON document
     */
    @Named("dtoToQueryString")
    default String dtoToQueryString(final QueryDTO dto) {
        return Lang2QueryDocuments.toQueryString(dto);
    }

    /**
     * Turns project ids into entity references for the query mapping.
     *
     * @param projectIds related project ids
     * @return project references, or {@code null} when none are set
     */
    @Named("queryDTOToProjectEntity")
    default Set<ProjectEntity> queryDTOToProjectEntity(final Set<Long> projectIds) {
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

    /**
     * Reads project ids from the mapped project references.
     *
     * @param projects related projects
     * @return project ids, or an empty set when none are set
     */
    @Named("projectEntityToIds")
    default Set<Long> projectEntityToIds(final Set<ProjectEntity> projects) {
        if (projects == null || projects.isEmpty()) {
            return new HashSet<>();
        }
        return projects.stream()
                .map(ProjectEntity::getId)
                .collect(Collectors.toSet());
    }
}

package bio.cosy.feddb.local.api.schema.schemanode;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.project.PatientExportFeatureDTO;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.SelectedDataIdsDTO;
import bio.cosy.feddb.core.api.query.QueryRejectedException;
import bio.cosy.feddb.core.api.query.item.QueryItemDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.learning.project.LearningProjectBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeNestedDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaRootNodeDTO;
import bio.cosy.feddb.local.api.search.SearchResultDTO;
import bio.cosy.feddb.local.api.search.SearchResultType;
import bio.cosy.feddb.local.api.search.SearchScoreUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class SchemaNodeBO extends BaseBo<LocalSchemaNodeDTO, SchemaNodeEntity, SchemaNodeAO, SchemaNodeMapper> {

    @Inject
    LearningProjectBO federatedLearningProjectBO;

    public List<LocalSchemaNodeDTO> getSchemaNodesForCohort(Long cohortId) {
        return mapper.entitiesToDtos(ao.getSchemaNodesForCohort(cohortId));
    }

    public List<LocalSchemaNodeWithPathDTO> getSchemaNodesWithPathForCohort(Long cohortId) {
        return ao.getSchemaNodesForCohort(cohortId).stream()
                .map(mapper::entityToNamedDto)
                .toList();
    }


    public List<SearchResultDTO<LocalSchemaNodeDTO>> search(String query, Set<Long> cohortIds) {
        return cohortIds.stream()
                .flatMap(cohortId -> getSchemaNodesForCohort(cohortId).stream())
                .map(node -> {
                    String title = node.getName();
                    int score = SearchScoreUtil.score(query, title, node.getDescription(), node.getGlobalId(),
                            node.getType() == null ? null : node.getType().name(),
                            node.getOntology() == null ? null : node.getOntology().getName(),
                            node.getDataType() == null ? null : node.getDataType().getName());
                    return SearchScoreUtil.toResult(SearchResultType.SCHEMA, node, title, score);
                })
                .filter(result -> result.getScore() > 0)
                .toList();
    }

    public Optional<LocalSchemaNodeDTO> findById(Long id) {
        return ao.findByIdOptional(id)
                .map(mapper::entityToDto);
    }

    public SchemaNodeEntity persistRoot(LocalSchemaRootNodeDTO remoteLoadedSchema) {
        // Create the local schema root node
        SchemaNodeEntity localRootNode = mapper.localSchemaToRootEntity(remoteLoadedSchema);
        ao.persist(localRootNode);
        // We need to persist to have an ID as this is done by the Database
        return localRootNode;
    }

    public SchemaNodeEntity initEntity(LocalSchemaNodeNestedDTO field, SchemaNodeEntity parent, Long depth) {
        SchemaNodeEntity localSchemaNode = mapper.dtoToEntity(field);
        localSchemaNode.setDepth(depth);
        localSchemaNode.setParent(parent);
        localSchemaNode.setOntology(null);
        localSchemaNode.setDataType(null);
        return localSchemaNode;
    }

    public List<LocalSchemaNodeDTO> getSchemaNodesForProject(Long projectId) {
        ProjectDetailDTO project = federatedLearningProjectBO.getById(projectId);
        if (project.getExportConfig() == null || project.getExportConfig().getFeatures() == null) {
            throw new NotFoundException("No export configuration found for project with ID " + projectId);
        }
        return project.getExportConfig().getFeatures().stream()
                .map(this::resolveSelectedDataId)
                .flatMap(id ->
                        ao.findByOntologyAndDatatypeGlobalId(id.getGlobalOntologyId(), id.getGlobalDataTypeId()).stream()
                )
                .map(mapper::entityToDto)
                .toList();
    }

    public LocalSchemaRootNodeDTO getSchemaNodesNestedForCohort(Long cohortID) {
        SchemaNodeEntity rootSchema = ao.getRootSchemaNodeForCohort(cohortID)
                .orElseThrow(() -> new NotFoundException("Cohort with ID " + cohortID + " has no root schema node assigned"));

        return mapper.entityToRoot(rootSchema);
    }

    private SelectedDataIdsDTO resolveSelectedDataId(PatientExportFeatureDTO feature) {
        if (feature == null || feature.getAllowedDataIds() == null || feature.getAllowedDataIds().isEmpty()) {
            throw new NotFoundException("No export configuration found for project");
        }

        return feature.getAllowedDataIds().stream()
                .filter(Objects::nonNull)
                .filter(dataId -> dataId.getGlobalDataTypeId() != null)
                .filter(dataId -> feature.getTargetDatatypeId() != null
                        && feature.getTargetDatatypeId().contains(dataId.getGlobalDataTypeId()))
                .findFirst()
                .orElse(feature.getAllowedDataIds().stream()
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElseThrow(() -> new NotFoundException("No export configuration found for project")));
    }

    /** Schema nodes of the ontology in all cohorts, of every data type when dataTypeId is blank; none without ontologyId. */
    public List<SchemaNodeEntity> findSchemaNodes(String ontologyId, String dataTypeId) {
        if (StringUtils.isBlank(ontologyId)) {
            return List.of();
        }
        if (StringUtils.isBlank(dataTypeId)) {
            return ao.findByOntologyGlobalId(ontologyId);
        }
        return ao.findByOntologyAndDatatypeGlobalId(ontologyId, dataTypeId);
    }

    public Map<DataTypes, List<Long>> findSchemaForQuery(QueryItemDTO leaf, Collection<Long> cohortIds) {
        if (cohortIds == null) {
            throw new QueryRejectedException("Cohort IDs must be provided to find schema node IDs by type");
        }
        if (StringUtils.isEmpty(leaf.getOntologyId()) || StringUtils.isEmpty(leaf.getDataTypeId())) {
            throw new QueryRejectedException("Ontology ID and Data Type ID must be provided to find schema node IDs by type");
        }
        List<SchemaNodeEntity> schemaNodes = ao.findByOntologyAndDatatypeGlobalIdAndCohortIds(leaf.getOntologyId(), leaf.getDataTypeId(), cohortIds);
        if (schemaNodes.isEmpty()) {
            throw new QueryRejectedException("Required condition cannot be found on this node");
        }
        return schemaNodes.stream().collect(Collectors.groupingBy(
                schemaNode -> schemaNode.getDataType().getType(),
                LinkedHashMap::new,
                Collectors.mapping(SchemaNodeEntity::getId, Collectors.toList())));
    }
}

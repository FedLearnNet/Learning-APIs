package bio.cosy.feddb.local.api.schema.schemanode;

import bio.cosy.feddb.core.api.project.PatientExportFeatureDTO;
import bio.cosy.feddb.core.api.project.ProjectDetailDTO;
import bio.cosy.feddb.core.api.project.SelectedDataIdsDTO;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.learning.project.FederatedLearningProjectBO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaNodeNestedDTO;
import bio.cosy.feddb.local.api.schema.LocalSchemaRootNodeDTO;
import bio.cosy.feddb.local.api.search.SearchResultDTO;
import bio.cosy.feddb.local.api.search.SearchResultType;
import bio.cosy.feddb.local.api.search.SearchScoreUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.NotFoundException;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class SchemaNodeBO extends BaseBo<LocalSchemaNodeDTO, SchemaNodeEntity, SchemaNodeAO, SchemaNodeMapper> {

    @Inject
    FederatedLearningProjectBO federatedLearningProjectBO;

    public List<LocalSchemaNodeDTO> getSchemaNodesForCohort(Long internalPatientId) {
        return mapper.entitiesToDtos(ao.getSchemaNodesForCohort(internalPatientId));
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


    public List<SimpleLocalSchemaNodeDTO> findSimpleByIds(Set<Long> schemaNodeIds) {
        return ao.findByIds(schemaNodeIds).stream()
                .map(mapper::entityToSimpleDto)
                .toList();
    }

    public Map<Long, SimpleLocalSchemaNodeDTO> findSimpleMapByIds(Set<Long> schemaNodeIds){
        return ao.findByIds(schemaNodeIds).stream()
                .filter(Objects::nonNull)
                .filter(node -> node.getId() != null)
                .collect(Collectors.toMap(SchemaNodeEntity::getId, mapper::entityToSimpleDto));
    }


}

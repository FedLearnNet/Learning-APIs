package bio.cosy.feddb.local.api.cohort.queryability;

import bio.cosy.feddb.core.api.query.QueryItemDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorDTO;
import bio.cosy.feddb.core.api.query.QueryOperatorTypes;
import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@ApplicationScoped
public class CohortQueryAbilityBO extends BaseBo<CohortQueryAbilityDTO, CohortQueryAbilityEntity, CohortQueryAbilityAO, CohortQueryAbilityMapper> {

    @Inject
    SchemaNodeAO schemaNodeAO;

    public List<CohortQueryAbilityDTO> list(long cohortId) {
        return mapper.entitiesToDtos(ao.findAll(cohortId));
    }

    public List<Long> filterQueryableCohortIds(List<QueryItemDTO> queryItems, List<Long> cohortIds) {
        if (queryItems == null || queryItems.isEmpty()) {
            return List.of();
        }
        List<ResolvedQueryItem> resolvedItems = queryItems.stream()
                .filter(Objects::nonNull)
                .map(item -> new ResolvedQueryItem(item, findSchemaNodes(item)))
                .toList();

        return cohortIds.stream()
                .filter(cohortId -> {
                    boolean queryable = isQueryableForCohort(cohortId, resolvedItems);
                    if (!queryable) {
                        Log.warnf("Cohort %s excluded from query due to queryability settings", cohortId);
                    }
                    return queryable;
                })
                .toList();
    }

    private boolean isQueryableForCohort(
            Long cohortId,
            List<ResolvedQueryItem> resolvedItems) {
        List<CohortQueryAbilityDTO> abilities = list(cohortId);

        return resolvedItems.stream()
                .allMatch(item -> {
                    List<SchemaNodeEntity> schemaNodes = item.getSchemaNodes().stream()
                            .filter(node -> node.getCohort() != null
                                    && cohortId.equals(node.getCohort().getId()))
                            .toList();
                    return !schemaNodes.isEmpty() && schemaNodes.stream()
                            .allMatch(node -> isQueryable(
                                    abilities.stream()
                                            .filter(ability -> ability.getSchemaNodeId().equals(node.getId()))
                                            .findFirst()
                                            .map(CohortQueryAbilityDTO::getQueryAbilityInfo)
                                            .orElse(QueryAbility.VALUE),
                                    item.getItem().getOperator()));
                });
    }

    public CohortQueryAbilityDTO findByIds(Long cohortId, Long id) {
        Optional<CohortQueryAbilityEntity> entityOptional = Optional.ofNullable(ao.findById(id));
        if (entityOptional.isEmpty()) {
            throw new NotFoundException("CohortQueryAbilityEntity not found");
        }
        if (!entityOptional.get().getCohort().getId().equals(cohortId)) {
            throw new BadRequestException("Cohort ID in path does not match the one in the entity");
        }
        return mapper.entityToDto(entityOptional.get());
    }


    public CohortQueryAbilityDTO create(Long cohortId, CreateCohortQueryAbilityDTO createDTO) {
        // cohortIs is given in the path and the dto, in theory unnecessary
        // but it makes the path clearer
        // We check if they match, otherwise the request is invalid
        if (!cohortId.equals(createDTO.getCohortId())) {
            throw new BadRequestException("Cohort ID in path does not match the one in the DTO");
        }
        return create(createDTO);
    }

    public List<CohortQueryAbilityDTO> createBulk(Long cohortId, List<CreateCohortQueryAbilityDTO> createDTO) {
        return createDTO.stream()
                .map(dto -> this.create(cohortId, dto))
                .toList();
    }

    public CohortQueryAbilityDTO update(Long cohortId, Long id, CohortQueryAbilityDTO updateDTO) {
        if (!cohortId.equals(updateDTO.getCohortId())) {
            throw new BadRequestException("Cohort ID in path does not match the one in the DTO");
        }
        return update(id, updateDTO);
    }

    public List<CohortQueryAbilityDTO> updateBulk(Long cohortId, List<CreateCohortQueryAbilityDTO> dtos) {
        return dtos.stream()
                .map(dto -> this.update(cohortId, dto.getId(), dto))
                .toList();
    }

    private List<SchemaNodeEntity> findSchemaNodes(QueryItemDTO item) {
        if (item.getOntologyId() == null || item.getOntologyId().isBlank()) {
            return List.of();
        }
        return schemaNodeAO.findByOntologyAndDatatypeGlobalId(item.getOntologyId(), item.getDataTypeId()).stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private boolean isQueryable(QueryAbility queryAbility, List<QueryOperatorDTO> operators) {
        if (queryAbility == QueryAbility.VALUE) {
            return true;
        }
        return queryAbility == QueryAbility.EXISTENCE && !needsValueAccess(operators);
    }

    private boolean needsValueAccess(List<QueryOperatorDTO> operators) {
        return operators == null || operators.stream()
                .filter(Objects::nonNull)
                .map(QueryOperatorDTO::getOperator)
                .anyMatch(operator -> operator != QueryOperatorTypes.EXISTS
                        && operator != QueryOperatorTypes.NOT_EXISTS);
    }

    public void delete(Long cohortId, Long id) {
        Optional<CohortQueryAbilityEntity> entityOptional = Optional.ofNullable(ao.findById(id));
        if (entityOptional.isEmpty()) {
            // we still return 200 as it is not here, it is deleted
            return;
        }
        CohortQueryAbilityEntity entity = entityOptional.get();
        if (!entity.getCohort().getId().equals(cohortId)) {
            return;
            // found an entity but not the one wanted to be deleted
            // should probably be a 404 but we return 200 as it is not here, it is deleted
        }
        deleteById(id);
    }
}

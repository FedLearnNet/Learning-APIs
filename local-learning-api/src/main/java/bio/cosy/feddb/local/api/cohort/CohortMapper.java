package bio.cosy.feddb.local.api.cohort;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.cohort.inclusion.CohortCriterionDTO;
import bio.cosy.feddb.local.api.cohort.inclusion.CohortCriterionEntity;
import bio.cosy.feddb.local.api.cohort.inclusion.CohortCriterionMapper;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberDTO;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberEntity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberMapper;
import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.schema.LocalSchemaRootNodeDTO;
import bio.cosy.feddb.local.api.schema.SchemaBO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeBO;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import jakarta.inject.Inject;
import org.mapstruct.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;


@Mapper(config = QuarkusMappingConfig.class)
public abstract class CohortMapper implements BaseMapper<CohortDTO, CohortEntity> {

    @Inject
    CohortMemberMapper memberMapper;

    @Inject
    SchemaBO schemaBO;

    @Inject
    SchemaNodeBO schemaNodeBO;

    @Inject
    CohortCriterionMapper cohortCriterionMapper;

    @Inject
    PatientAO patientAO;

    @Mappings({
            @Mapping(target = "internalSchemaId", ignore = true),
            @Mapping(target = "globalSchemaId", ignore = true),
            @Mapping(target = "amountOfPatients", ignore = true),
            @Mapping(target = "criteria", source = "criteria", qualifiedByName = "mapCriteria"),
            @Mapping(target = "deletionInProgress", ignore = true),
    })
    public abstract CohortDTO entityToDto(CohortEntity entity);

    @Mappings({
            @Mapping(target = "internalSchemaId", ignore = true),
            @Mapping(target = "globalSchemaId", ignore = true),
            @Mapping(target = "amountOfPatients", ignore = true),
            @Mapping(target = "schemaRoot", ignore = true),
            @Mapping(target = "members", source = "members", qualifiedByName = "mapMembers"),
            @Mapping(target = "criteria", source = "criteria", qualifiedByName = "mapCriteria"),
            @Mapping(target = "deletionInProgress", ignore = true),
    })
    public abstract CohortDetailDTO entityToDetailDto(CohortEntity entity);

    @Mappings({
            @Mapping(target = "schemaNodes", ignore = true),
            @Mapping(target = "patients", ignore = true),
            @Mapping(target = "permissions", ignore = true),
            @Mapping(target = "queryabilities", ignore = true),
            @Mapping(target = "connectorFiles", ignore = true),
            @Mapping(target = "members", ignore = true),
            @Mapping(target = "criteria", ignore = true)
    })
    public abstract CohortEntity dtoToEntity(CohortDTO dto);


    @Mappings({
            @Mapping(target = "keycloakId", source = "keycloakId"),
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "patients", ignore = true),
            @Mapping(target = "permissions", ignore = true),
            @Mapping(target = "queryabilities", ignore = true),
            @Mapping(target = "schemaNodes", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
            @Mapping(target = "connectorFiles", ignore = true),
            @Mapping(target = "members", ignore = true),
            @Mapping(target = "criteria", ignore = true),
            @Mapping(target = "approvalDate", ignore = true)
    })
    public abstract CohortEntity createDtoToEntity(CreateCohortDTO dto, String keycloakId);

    @AfterMapping
    public void afterMapping(CohortDTO dto, @MappingTarget CohortEntity entity) {
        // Find/Subscribe to the schema and connect the nodes
        entity.setSchemaNodes(
                schemaBO.getCohortNodes(dto.getId(), UUID.fromString(dto.getGlobalSchemaId()))
        );
    }

    private LocalSchemaRootNodeDTO mapBase(CohortEntity entity, CohortDTO dto) {
        LocalSchemaRootNodeDTO root = schemaNodeBO.getSchemaNodesNestedForCohort(entity.getId());
        if (root == null) {
            throw new IllegalStateException("Root node not found for cohort: " + entity.getName());
        }
        dto.setInternalSchemaId(root.getId());
        dto.setGlobalSchemaId(root.getGlobalId());
        dto.setAmountOfPatients((int) patientAO.countByCohortId(entity.getId()));
        return root;
    }

    @AfterMapping
    public void afterMapping(CohortEntity entity, @MappingTarget CohortDTO dto) {
        mapBase(entity, dto);
    }

    @AfterMapping
    public void afterMappingDetail(CohortEntity entity, @MappingTarget CohortDetailDTO dto) {
        LocalSchemaRootNodeDTO root = mapBase(entity, dto);
        dto.setSchemaRoot(root);
    }

    @Named("mapMembers")
    public List<CohortMemberDTO> mapMembers(Set<CohortMemberEntity> members) {
        if (members == null) {
            return null;
        }
        return memberMapper.entitiesToDtos(members);
    }

    @Named("mapCriteria")
    protected List<CohortCriterionDTO> mapCriteria(Set<CohortCriterionEntity> entities) {
        if (entities == null) return null;
        return entities.stream().map(cohortCriterionMapper::entityToDto).toList();
    }
}

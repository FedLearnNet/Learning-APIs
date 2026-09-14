package bio.cosy.feddb.local.api.cohort.queryability;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeAO;
import bio.cosy.feddb.local.api.schema.schemanode.SchemaNodeEntity;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import jakarta.inject.Inject;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;
import java.util.List;

@Mapper(config = QuarkusMappingConfig.class)
public interface CohortQueryAbilityMapper extends BaseMapper<CohortQueryAbilityDTO,CohortQueryAbilityEntity> {

    @Mappings({
            @Mapping(target = "cohortId", source = "cohort.id"),
            @Mapping(target = "schemaNodeId", source = "schemaNode.id"),
    })
    CohortQueryAbilityDTO entityToDto(CohortQueryAbilityEntity entity);

    @Mappings({
            @Mapping(target = "cohort.id", source = "cohortId"),
            @Mapping(target = "schemaNode.id", source = "schemaNodeId"),
    })
    CohortQueryAbilityEntity dtoToEntity(CohortQueryAbilityDTO dto);
}

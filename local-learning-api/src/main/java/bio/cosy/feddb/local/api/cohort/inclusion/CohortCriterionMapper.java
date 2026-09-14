package bio.cosy.feddb.local.api.cohort.inclusion;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface CohortCriterionMapper extends BaseMapper<CohortCriterionDTO, CohortCriterionEntity> {

    @Mapping(target = "cohort.id", source = "cohortId")
    CohortCriterionEntity dtoToEntity(CohortCriterionDTO dto);

    @Mapping(target = "cohortId", source = "cohort.id")
    CohortCriterionDTO entityToDto(CohortCriterionEntity entity);
}

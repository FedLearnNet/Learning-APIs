package bio.cosy.feddb.local.api.cohort.statistics.dashboard;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = QuarkusMappingConfig.class)
public interface CustomStatisticsDashboardMapper extends BaseMapper<CustomStatisticsDashboardDTO, CustomStatisticsDashboardEntity> {

    @Mapping(target = "cohortId", source = "cohort.id")
    CustomStatisticsDashboardDTO entityToDto(CustomStatisticsDashboardEntity entity);

    @Mapping(target = "cohort.id", source = "cohortId")
    @Mapping(target = "statistics", ignore = true)
    CustomStatisticsDashboardEntity dtoToEntity(CustomStatisticsDashboardDTO dto);
}

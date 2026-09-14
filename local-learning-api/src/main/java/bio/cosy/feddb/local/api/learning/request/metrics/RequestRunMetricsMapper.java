package bio.cosy.feddb.local.api.learning.request.metrics;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Mappings;

@Mapper(config = QuarkusMappingConfig.class)
public interface RequestRunMetricsMapper extends BaseMapper<RequestRunMetricsDTO, RequestRunMetricsEntity> {

    @Mappings({
            @Mapping(target = "projectId", source = "project.id"),
            @Mapping(target = "projectName", source = "project.name"),
            @Mapping(target = "experimentGlobalUniqueId", source = "project.request.globalFLExperimentUniqueId"),
            @Mapping(target = "metricNames", ignore = true)
    })
    RequestRunMetricsDTO entityToDto(RequestRunMetricsEntity entity);

    @Mappings({
            @Mapping(target = "project.id", source = "projectId")
    })
    RequestRunMetricsEntity dtoToEntity(RequestRunMetricsDTO dto);
}

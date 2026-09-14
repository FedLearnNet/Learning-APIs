package bio.cosy.feddb.local.api.cohort.statistics.entry;

import bio.cosy.feddb.core.base.BaseMapper;
import bio.cosy.feddb.local.api.cohort.statistics.entry.config.CustomStatisticConfig;
import bio.cosy.feddb.local.api.cohort.statistics.entry.data.CustomStatisticData;
import bio.cosy.feddb.local.helper.QuarkusMappingConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import org.mapstruct.*;

@Mapper(config = QuarkusMappingConfig.class)
public abstract class CustomStatisticMapper implements BaseMapper<CustomStatisticDTO, CustomStatisticEntity> {

    @Inject
    CustomStatisticCalculator calculator;

    @Inject
    ObjectMapper objectMapper;

    @Mapping(target = "data", ignore = true)
    @Mapping(target = "dashboardId", source = "dashboard.id")
    public abstract CustomStatisticDTO entityToDto(CustomStatisticEntity entity);

    @Mapping(target = "dashboard.id", source = "dashboardId")
    public abstract CustomStatisticEntity dtoToEntity(CustomStatisticDTO dto);

    @Mappings({
            @Mapping(target = "createdAt", ignore = true),
            @Mapping(target = "dashboard.id", source = "dashboardId"),
            @Mapping(target = "id", ignore = true),
            @Mapping(target = "sortOrder", ignore = true),
            @Mapping(target = "updatedAt", ignore = true),
            @Mapping(target = "version", ignore = true),
    })
    public abstract CustomStatisticEntity createDtoToEntity(CreateCustomStatisticDTO dto);

    @AfterMapping
    public void afterMapping(CustomStatisticEntity entity, @MappingTarget CustomStatisticDTO dto) {
        Long cohortId = entity.getDashboard().getCohort().getId();
        CustomStatisticConfig config = entity.getConfig();
        dto.setData(calc(config, cohortId));
    }

    public JsonNode calc(CustomStatisticConfig config, Long cohortId) {
        if (config == null) return null;
        CustomStatisticData data = calculator.calc(config, cohortId);
        return objectMapper.valueToTree(data);
    }
}

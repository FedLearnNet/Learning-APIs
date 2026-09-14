package bio.cosy.feddb.local.api.cohort.statistics.entry;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.statistics.dashboard.CustomStatisticsDashboardBO;
import bio.cosy.feddb.local.api.cohort.statistics.dashboard.CustomStatisticsDashboardEntity;
import bio.cosy.feddb.local.api.cohort.statistics.entry.config.CustomStatisticConfig;
import bio.cosy.feddb.local.api.cohort.statistics.entry.data.CustomStatisticData;
import bio.cosy.feddb.local.api.statistics.DataStatisticsBO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class CustomStatisticBO extends BaseBo<
        CustomStatisticDTO,
        CustomStatisticEntity,
        CustomStatisticAO,
        CustomStatisticMapper> {

    @Inject
    CustomStatisticsDashboardBO dashboardBO;

    public List<CustomStatisticDTO> listForDashboard(Long dashboardId, String keycloakId) {
        dashboardBO.findOwnedOrThrow(dashboardId, keycloakId, false);
        return ao.listByDashboard(dashboardId).stream()
                .map(mapper::entityToDto)
                .toList();
    }

    public CustomStatisticDTO findById(Long id, String keycloakId) {
        CustomStatisticEntity entity = findOwnedOrThrow(id, keycloakId, false);
        return mapper.entityToDto(entity);
    }

    @Transactional
    public CustomStatisticDTO create(CreateCustomStatisticDTO createDTO, String keycloakId) {
        CustomStatisticsDashboardEntity dashboard = dashboardBO.findOwnedOrThrow(
                createDTO.getDashboardId(), keycloakId, true);

        int nextSortOrder = ao.listByDashboard(dashboard.getId()).size();

        CustomStatisticEntity entity = mapper.createDtoToEntity(createDTO);
        // MapStruct produces a stub dashboard with only id set; replace it with the
        // fully-loaded one so its cohort relation is available for downstream computation
        // (e.g. CustomStatisticMapper#afterMapping reading dashboard.cohort.id).
        entity.setDashboard(dashboard);
        entity.setSortOrder(nextSortOrder);
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }

    @Transactional
    public CustomStatisticDTO update(Long id, CustomStatisticDTO updateDTO, String keycloakId) {
        CustomStatisticEntity entity = findOwnedOrThrow(id, keycloakId, true);
        if (updateDTO.getName() != null) {
            entity.setName(updateDTO.getName());
        }
        if (updateDTO.getConfig() != null) {
            entity.setConfig(updateDTO.getConfig());
        }
        if (updateDTO.getSortOrder() != null) {
            entity.setSortOrder(updateDTO.getSortOrder());
        }
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }

    @Transactional
    public void delete(Long id, String keycloakId) {
        CustomStatisticEntity entity = findOwnedOrThrow(id, keycloakId, true);
        ao.delete(entity);
    }

    private CustomStatisticEntity findOwnedOrThrow(Long id, String keycloakId, boolean requireEdit) {
        CustomStatisticEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Custom statistic not found"));
        dashboardBO.findOwnedOrThrow(entity.getDashboard().getId(), keycloakId, requireEdit);
        return entity;
    }
}

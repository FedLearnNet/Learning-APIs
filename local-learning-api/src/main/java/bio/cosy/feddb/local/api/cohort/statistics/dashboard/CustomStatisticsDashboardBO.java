package bio.cosy.feddb.local.api.cohort.statistics.dashboard;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.CohortAO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class CustomStatisticsDashboardBO extends BaseBo<
        CustomStatisticsDashboardDTO,
        CustomStatisticsDashboardEntity,
        CustomStatisticsDashboardAO,
        CustomStatisticsDashboardMapper> {

    @Inject
    CohortAO cohortAO;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    public List<CustomStatisticsDashboardDTO> listForCohort(Long cohortId, String keycloakId) {
        cohortMemberAuthBO.checkForMember(cohortId, keycloakId);
        return ao.listByCohort(cohortId).stream()
                .map(mapper::entityToDto)
                .toList();
    }

    @Transactional
    public CustomStatisticsDashboardDTO create(CreateCustomStatisticsDashboardDTO createDTO, String keycloakId) {
        cohortMemberAuthBO.checkForEdit(createDTO.getCohortId(), keycloakId);
        CohortEntity cohort = cohortAO.findByIdOptional(createDTO.getCohortId())
                .orElseThrow(() -> new NotFoundException("Cohort not found"));

        int nextSortOrder = ao.listByCohort(cohort.getId()).size();

        CustomStatisticsDashboardEntity entity = new CustomStatisticsDashboardEntity();
        entity.setCohort(cohort);
        entity.setName(createDTO.getName());
        entity.setSortOrder(nextSortOrder);
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }

    @Transactional
    public CustomStatisticsDashboardDTO update(Long id, CustomStatisticsDashboardDTO updateDTO, String keycloakId) {
        CustomStatisticsDashboardEntity entity = findOwnedOrThrow(id, keycloakId, true);
        if (updateDTO.getName() != null) {
            entity.setName(updateDTO.getName());
        }
        if (updateDTO.getSortOrder() != null) {
            entity.setSortOrder(updateDTO.getSortOrder());
        }
        ao.persist(entity);
        return mapper.entityToDto(entity);
    }

    @Transactional
    public void delete(Long id, String keycloakId) {
        CustomStatisticsDashboardEntity entity = findOwnedOrThrow(id, keycloakId, true);
        ao.delete(entity);
    }

    public CustomStatisticsDashboardEntity findOwnedOrThrow(Long id, String keycloakId, boolean requireEdit) {
        CustomStatisticsDashboardEntity entity = ao.findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("Dashboard not found"));
        Long cohortId = entity.getCohort().getId();
        if (requireEdit) {
            cohortMemberAuthBO.checkForEdit(cohortId, keycloakId);
        } else {
            cohortMemberAuthBO.checkForMember(cohortId, keycloakId);
        }
        return entity;
    }
}

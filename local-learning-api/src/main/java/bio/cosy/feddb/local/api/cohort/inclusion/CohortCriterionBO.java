package bio.cosy.feddb.local.api.cohort.inclusion;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class CohortCriterionBO extends BaseBo<CohortCriterionDTO, CohortCriterionEntity, CohortCriterionAO, CohortCriterionMapper> {

    public Set<CohortCriterionEntity> toEntities(CohortEntity cohort, List<CohortCriterionDTO> criteria) {
        Set<CohortCriterionEntity> entities = new LinkedHashSet<>();
        if (criteria == null) {
            return entities;
        }
        for (CohortCriterionDTO criterion : criteria) {
            CohortCriterionEntity entity = mapper.dtoToEntity(criterion);
            setBaseValuesNull(entity);
            entity.setCohort(cohort);
            entities.add(entity);
        }
        return entities;
    }

    public void replaceCriteria(CohortEntity cohort, List<CohortCriterionDTO> criteria) {
        if (cohort.getCriteria() == null) {
            cohort.setCriteria(new LinkedHashSet<>());
        }
        cohort.getCriteria().clear();
        cohort.getCriteria().addAll(toEntities(cohort, criteria));
    }
}

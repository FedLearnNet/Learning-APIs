package bio.cosy.feddb.local.api.cohort.permission;

import bio.cosy.feddb.core.base.BaseBo;
import bio.cosy.feddb.local.api.cohort.CohortBO;
import bio.cosy.feddb.local.api.cohort.CohortDTO;
import bio.cosy.feddb.local.api.cohort.CohortEntity;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;

import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class PermissionBO extends BaseBo<PermissionDTO, PermissionEntity, PermissionAO, PermissionMapper> {

    @Inject
    CohortBO cohortBO;

    @Inject
    FLNetClientConfig config;


    /**
     * Returns the most permissive (minimum) positive queryRetryTime per cohort across all active
     * query-allowed permissions for the given user. Only permissions with isAllowedToQuery=true
     * and a positive retryTime are considered. Returns an empty map if no such permissions exist.
     */
    public Map<Long, Integer> getQueryRetryTimeByCohort(String userId) {
        return ao.isAllowedToQuery(userId).stream()
                .filter(p -> p.getQueryRetryTime() != null && p.getQueryRetryTime() > 0)
                .collect(Collectors.groupingBy(
                        p -> p.getCohort().getId(),
                        Collectors.collectingAndThen(
                                Collectors.minBy(Comparator.comparingInt(PermissionEntity::getQueryRetryTime)),
                                opt -> opt.map(PermissionEntity::getQueryRetryTime).orElseThrow()
                        )
                ));
    }

    public List<PermissionDTO> getAllowedToQueryCohortIds(String userId) {
        if (userId == null) {
            Log.warn("userId cannot be null");
            return new ArrayList<>();
        }
        return mapper.entitiesToDtos(ao.isAllowedToQuery(userId));
    }

    /**
     * Returns true if the user has auto-training access for <b>every</b> given cohort. A learning
     * request covers all cohorts matched by its query at once, so a single cohort without
     * auto-training access is enough to send the whole request to manual approval.
     * A cohort counts as granted when at least one of its active permissions allows it; a cohort
     * without any matching permission is never granted.
     * userId must exactly match the stored permission value as validated by the global system.
     * Permissions without a userId apply globally.
     * null or NONE denies access; ALL allows it. CERTIFIED_APPS requires certificationLevel > 0.
     */
    public boolean hasAutoTrainingAccess(String userId, Set<Long> cohortIds, int certificationLevel,
                                         boolean needsInternetAccess) {
        if (cohortIds.isEmpty()) return true;
        if (needsInternetAccess) {
            Log.infof("Auto-training access denied for user %s: the workflow needs internet access", userId);
            return false;
        }

        Set<Long> grantedCohortIds = ao.getActivePermissionsForCohorts(userId, cohortIds).stream()
                .filter(p -> grantsAutoTraining(p.getAutoTrainingAccess(), certificationLevel))
                .map(p -> p.getCohort().getId())
                .collect(Collectors.toSet());

        if (grantedCohortIds.containsAll(cohortIds)) {
            Log.infof("Auto-training access granted for user %s on all cohorts %s (certificationLevel=%d)",
                    userId, cohortIds, certificationLevel);
            return true;
        }

        Set<Long> missing = new HashSet<>(cohortIds);
        missing.removeAll(grantedCohortIds);
        Log.infof("Auto-training access denied for user %s: cohorts %s of %s require manual approval "
                        + "(certificationLevel=%d)", userId, missing, cohortIds, certificationLevel);
        return false;
    }

    private boolean grantsAutoTraining(AutoTrainingAccess access, int certificationLevel) {
        if (access == AutoTrainingAccess.ALL) return true;
        if (access == AutoTrainingAccess.CERTIFIED_APPS) return certificationLevel > 0;
        return false; // null or NONE
    }

    /**
     * Returns true if the user has training access for the given cohort. A training request can
     * span cohorts with different settings, so every cohort is evaluated on its own instead of
     * letting one permissive cohort approve the whole request.
     */
    public boolean hasAutoTrainingAccessForCohort(String userId, Long cohortId,
                                                  int certificationLevel,
                                                  boolean needsInternetAccess) {
        if (cohortId == null || needsInternetAccess) {
            return false;
        }
        return ao.getActivePermissionsForCohort(userId, cohortId).stream().anyMatch(p -> {
            AutoTrainingAccess access = p.getAutoTrainingAccess();
            if (access == AutoTrainingAccess.ALL) return true;
            if (access == AutoTrainingAccess.CERTIFIED_APPS) return certificationLevel > 0;
            return false; // null or NONE
        });
    }

    /**
     * Returns true if the user has statistics access for at least one of the given cohorts.
     * userId must exactly match the stored permission value as validated by the global system.
     * Permissions without a userId apply globally.
     * null or NONE denies access; ALL allows it.
     */
    public boolean hasAutoStatisticsAccess(String userId, Long cohortId) {
        if (cohortId == null) {
            return false;
        }
        List<PermissionEntity> permissions = ao.getActivePermissionsForCohort(userId, cohortId);
        return permissions.stream().anyMatch(p -> {
            AutoStatisticsAccess access = p.getAutoStatisticsAccess();
            return access == AutoStatisticsAccess.ALL;
        });
    }

    /**
     * Returns true if the user has run-metrics access for the given cohort. Same semantics as
     * {@link #hasAutoStatisticsAccess}: a run-metrics request for a cohort with ALL is auto-approved,
     * null or NONE requires manual approval.
     */
    public boolean hasAutoMetricsAccess(String userId, Long cohortId) {
        if (cohortId == null) {
            return false;
        }
        List<PermissionEntity> permissions = ao.getActivePermissionsForCohort(userId, cohortId);
        return permissions.stream().anyMatch(p -> p.getAutoMetricsAccess() == AutoMetricsAccess.ALL);
    }

    public PermissionDTO findById(Long id) {
        Optional<PermissionEntity> entityOptional = ao.findByIdOptional(id);
        if (entityOptional.isEmpty()) {
            throw new NotFoundException("Permission not found");
        }
        return mapper.entityToDto(entityOptional.get());
    }


    public PermissionDTO create(PermissionDTO permission) {
        cohortExists(permission);

        if (config.cohort().deactivateAutomaticCohortPermission().learning()
                && permission.getAutoTrainingAccess() != null
                && permission.getAutoTrainingAccess() != AutoTrainingAccess.NONE
        ) {
            Log.warnf("Automatic training access is deactivated in the configuration, cannot create permission with training access");
            throw new NotAllowedException("Automatic training access is deactivated in the configuration, " +
                    "cannot create permission with training access");
        }
        if (config.cohort().deactivateAutomaticCohortPermission().metrics()
                && permission.getAutoMetricsAccess() != null
                && permission.getAutoMetricsAccess() != AutoMetricsAccess.NONE
        ) {
            Log.warnf("Automatic metrics access is deactivated in the configuration, cannot create permission with metrics access");
            throw new NotAllowedException("Automatic metrics access is deactivated in the configuration, " +
                    "cannot create permission with metrics access");
        }
        if (config.cohort().deactivateAutomaticCohortPermission().statistics()
                && permission.getAutoStatisticsAccess() != null
                && permission.getAutoStatisticsAccess() != AutoStatisticsAccess.NONE
        ) {
            Log.warnf("Automatic statistics access is deactivated in the configuration, cannot create permission with statistics access");
            throw new NotAllowedException("Automatic statistics access is deactivated in the configuration, " +
                    "cannot create permission with statistics access");
        }
        PermissionEntity newPermission = mapper.dtoToEntity(permission);
        ao.persist(newPermission);
        return mapper.entityToDto(newPermission);
    }

    public PermissionDTO update(Long id, PermissionDTO permission) {
        this.cohortExists(permission);
        return super.update(id, permission);
    }


    public void setDefault(PermissionDTO permission, FLNetClientConfig.DefaultCohortPermission permissionConfig) {
        permission.setUserId(permissionConfig.globalUserId().orElse(null));
        permission.setAutoTrainingAccess(permissionConfig.autoTrainingAccess());
        permission.setAutoStatisticsAccess(permissionConfig.autoStatisticsAccess());
        permission.setAutoMetricsAccess(permissionConfig.autoMetricsAccess());
        permission.setIsAllowedToQuery(permissionConfig.isAllowedToQuery());
        permission.setQueryRetryTime(permissionConfig.queryRetryTime());
        permission.setQuerySampleThreshold(permissionConfig.querySampleThreshold());
    }

    public void setDefault(PermissionEntity permission, FLNetClientConfig.DefaultCohortPermission permissionConfig) {
        permission.setUserId(permissionConfig.globalUserId().orElse(null));
        permission.setAutoTrainingAccess(permissionConfig.autoTrainingAccess());
        permission.setAutoStatisticsAccess(permissionConfig.autoStatisticsAccess());
        permission.setAutoMetricsAccess(permissionConfig.autoMetricsAccess());
        permission.setIsAllowedToQuery(permissionConfig.isAllowedToQuery());
        permission.setQueryRetryTime(permissionConfig.queryRetryTime());
        permission.setQuerySampleThreshold(permissionConfig.querySampleThreshold());
    }

    public void addDefault(CohortEntity cohortEntity) {
        FLNetClientConfig.DefaultCohortPermission permissionConfig = config.cohort().defaultCohortPermission();
        if (!permissionConfig.enabled()) {
            Log.info("Skipping adding default permission as it is disabled in the configuration");
            return;
        }
        // Add a default permission for the cohort
        Log.info("Adding default permission for cohort " + cohortEntity.getName());


        PermissionEntity permission = new PermissionEntity();
        permission.setCohort(cohortEntity);
        setDefault(permission, permissionConfig);

        if (config.cohort().deactivateAutomaticCohortPermission().learning()) {
            permission.setAutoTrainingAccess(AutoTrainingAccess.NONE);
        }
        if (config.cohort().deactivateAutomaticCohortPermission().metrics()) {
            permission.setAutoMetricsAccess(AutoMetricsAccess.NONE);
        }
        if (config.cohort().deactivateAutomaticCohortPermission().statistics()) {
            permission.setAutoStatisticsAccess(AutoStatisticsAccess.NONE);
        }
        ao.persist(permission);
    }

    private void cohortExists(PermissionDTO permission) {
        // Helper to check if a cohort for a given permission exists
        CohortDTO cohort = cohortBO.findById(permission.getCohortId());
        if (cohort == null) {
            throw new BadRequestException("Cohort with ID " + permission.getCohortId() + " does not exist, cannot create permission");
        }
    }
}

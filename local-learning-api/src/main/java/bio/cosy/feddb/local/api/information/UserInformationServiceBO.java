package bio.cosy.feddb.local.api.information;

import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestBO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestDTO;
import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestStatus;
import bio.cosy.feddb.local.api.learning.request.metrics.RequestRunMetricsBO;
import bio.cosy.feddb.local.api.learning.request.metrics.RequestRunMetricsDTO;
import bio.cosy.feddb.local.api.notification.NotificationBO;
import bio.cosy.feddb.local.api.statistics.request.RequestDataStatisticsBO;
import bio.cosy.feddb.local.api.statistics.request.RequestDataStatisticsDTO;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserInformationServiceBO {

    private static final Page ALL_PENDING = Page.of(0, Integer.MAX_VALUE);

    @Inject
    NotificationBO notificationBO;

    @Inject
    FederatedLearningRequestBO federatedLearningRequestBO;

    @Inject
    RequestDataStatisticsBO requestDataStatisticsBO;

    @Inject
    RequestRunMetricsBO requestRunMetricsBO;

    @Inject
    CohortMemberAuthBO cohortMemberAuthBO;

    public UserInformationDTO getBaseUserInformation(String keycloakId) {
        UserInformationDTO dto = new UserInformationDTO();
        dto.setNotifications(notificationBO.findActiveForUser(keycloakId));
        dto.setOpenTrainingRequests(countOpenTrainingRequests(keycloakId));
        dto.setOpenStatisticsRequests(countOpenStatisticsRequests(keycloakId));
        dto.setOpenMetricsRequests(countOpenMetricsRequests(keycloakId));
        return dto;
    }

    private Long countOpenTrainingRequests(String keycloakId) {
        return federatedLearningRequestBO.list(ALL_PENDING, FederatedLearningRequestStatus.PENDING, keycloakId)
                .getResults().stream()
                .filter(FederatedLearningRequestDTO::isAwaitingCurrentUserDecision)
                .count();
    }

    private Long countOpenStatisticsRequests(String keycloakId) {
        return requestDataStatisticsBO.list(ALL_PENDING, FederatedLearningRequestStatus.PENDING).getResults().stream()
                .filter(dto -> canAccessStatisticsRequest(dto, keycloakId))
                .count();
    }

    private boolean canAccessStatisticsRequest(RequestDataStatisticsDTO dto, String keycloakId) {
        return dto.getCohortIds().stream()
                .allMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId));
    }

    private Long countOpenMetricsRequests(String keycloakId) {
        return requestRunMetricsBO.list(ALL_PENDING, FederatedLearningRequestStatus.PENDING).getResults().stream()
                .map(RequestRunMetricsDTO::getId)
                .filter(id -> requestRunMetricsBO.getCohortIds(id).stream()
                        .allMatch(cohortId -> cohortMemberAuthBO.isMember(cohortId, keycloakId)))
                .count();
    }
}

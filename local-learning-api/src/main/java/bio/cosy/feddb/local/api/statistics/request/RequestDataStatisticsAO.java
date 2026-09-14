package bio.cosy.feddb.local.api.statistics.request;

import bio.cosy.feddb.local.api.learning.request.FederatedLearningRequestStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class RequestDataStatisticsAO implements PanacheRepository<RequestDataStatisticsEntity> {

    private static final String PATIENT_FILTER = """
            query.id in (
                select queryPatient.query.id
                from QueryPatientEntity queryPatient
                where queryPatient.patient.id = ?1
            )
            """;

    private static final String STATUS_AND_PATIENT_FILTER = """
            status = ?1 and query.id in (
                select queryPatient.query.id
                from QueryPatientEntity queryPatient
                where queryPatient.patient.id = ?2
            )
            """;

    public List<RequestDataStatisticsEntity> list(Page page) {
        return findAll().page(page).list();
    }

    public List<RequestDataStatisticsEntity> list(
            Page page,
            FederatedLearningRequestStatus status) {
        return find("status = ?1", status).page(page).list();
    }

    public List<RequestDataStatisticsEntity> list(
            Page page,
            Long patientId) {
        return find(PATIENT_FILTER, patientId).page(page).list();
    }

    public List<RequestDataStatisticsEntity> list(
            Page page,
            FederatedLearningRequestStatus status,
            Long patientId) {
        return find(STATUS_AND_PATIENT_FILTER, status, patientId)
                .page(page)
                .list();
    }
}

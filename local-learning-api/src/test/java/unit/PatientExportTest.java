package unit;

import bio.cosy.feddb.core.api.project.SelectedDataIdsDTO;
import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryExportDTO;
import bio.cosy.feddb.local.api.cohort.patient.export.PatientDataExportBO;
import bio.cosy.feddb.local.api.learning.request.cohort.FederatedLearningRequestCohortAO;
import bio.cosy.feddb.local.api.learning.request.cohort.FederatedLearningRequestCohortStatus;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


@QuarkusTest
public class PatientExportTest {

    private static final long REQUEST_ID = 3L;
    private static final long APPROVED_COHORT = 1L;
    private static final long PENDING_COHORT = 2L;
    private static final long REJECTED_COHORT = 3L;

    private static final List<SelectedDataIdsDTO> AGE_HEIGHT_BMI = List.of(
            new SelectedDataIdsDTO("2002", "1002"),
            new SelectedDataIdsDTO("2003", "1002"),
            new SelectedDataIdsDTO("2005", "1002"));

    @Inject
    PatientDataExportBO exportBO;

    @Inject
    PatientAO patientAO;

    @Inject
    FederatedLearningRequestCohortAO decisionAO;

    @Test
    @TestTransaction
    void exportsOnlyPatientsOfApprovedCohorts() {
        decide(APPROVED_COHORT, FederatedLearningRequestCohortStatus.APPROVED);
        decide(PENDING_COHORT, FederatedLearningRequestCohortStatus.PENDING);
        decide(REJECTED_COHORT, FederatedLearningRequestCohortStatus.REJECTED);

        assertEquals(Set.of(1L, 2L), Set.copyOf(patientAO.findApprovedPatientIdsByLearningRequestId(REQUEST_ID)));
        assertEquals(Set.of(1L, 2L), exportedPatientIds(),
                "only the approved cohort's patients may be exported, not the pending or rejected ones");
    }

    @Test
    @TestTransaction
    void exportsNothingWhileCohortsArePendingOrRejected() {
        decide(APPROVED_COHORT, FederatedLearningRequestCohortStatus.PENDING);
        decide(PENDING_COHORT, FederatedLearningRequestCohortStatus.PENDING);
        decide(REJECTED_COHORT, FederatedLearningRequestCohortStatus.REJECTED);

        assertTrue(patientAO.findApprovedPatientIdsByLearningRequestId(REQUEST_ID).isEmpty());
        assertTrue(exportBO.getDataForProject(REQUEST_ID, AGE_HEIGHT_BMI).isEmpty());
    }

    @Test
    @TestTransaction
    void exportsEveryCohortOnceAllHaveApproved() {
        decide(APPROVED_COHORT, FederatedLearningRequestCohortStatus.APPROVED);
        decide(PENDING_COHORT, FederatedLearningRequestCohortStatus.APPROVED);
        decide(REJECTED_COHORT, FederatedLearningRequestCohortStatus.APPROVED);

        List<Long> ids = patientAO.findApprovedPatientIdsByLearningRequestId(REQUEST_ID);
        assertEquals(5, ids.size(), "each patient has to be returned exactly once");
        assertEquals(Set.of(1L, 2L, 3L, 4L, 5L), Set.copyOf(ids));
        assertEquals(Set.of(1L, 2L, 3L, 4L, 5L), exportedPatientIds());
    }

    @Test
    @TestTransaction
    void exportsNothingForARequestWithoutCohortDecisions() {
        decisionAO.delete("request.id", REQUEST_ID);

        assertTrue(patientAO.findApprovedPatientIdsByLearningRequestId(REQUEST_ID).isEmpty());
        assertTrue(exportBO.getDataForProject(REQUEST_ID, AGE_HEIGHT_BMI).isEmpty());
    }

    private void decide(long cohortId, FederatedLearningRequestCohortStatus status) {
        int updated = decisionAO.update("status = ?1 where request.id = ?2 and cohort.id = ?3",
                status, REQUEST_ID, cohortId);
        assertEquals(1, updated, "the seed needs a decision of cohort " + cohortId + " on request " + REQUEST_ID);
    }

    private Set<Long> exportedPatientIds() {
        List<PatientDataEntryExportDTO> export = exportBO.getDataForProject(REQUEST_ID, AGE_HEIGHT_BMI);
        assertTrue(!export.isEmpty(), "the export should not be empty");
        return export.stream()
                .map(PatientDataEntryExportDTO::getPatientId)
                .collect(Collectors.toSet());
    }
}

package unit;

import bio.cosy.feddb.core.api.project.PatientDataExportConfigDTO;
import bio.cosy.feddb.core.api.project.PatientExportFilterDTO;
import bio.cosy.feddb.local.api.cohort.patient.PatientAO;
import bio.cosy.feddb.local.api.cohort.patient.PatientReferenceDTO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryAO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryExportDTO;
import bio.cosy.feddb.local.api.cohort.patient.export.PatientDataExportBO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import java.util.stream.Stream;

@QuarkusTest
public class PatientDataExportFilterTest {

    private static final Long COHORT_ID = 1L;

    @Inject
    PatientDataExportBO exportBO;

    @Inject
    PatientAO patientAO;

    @Inject
    PatientDataEntryAO patientDataEntryAO;

    /**
     * PostgreSQL rejects a statement with more than 65535 bind parameters, which is what a
     * cohort-wide export used to send in a single {@code IN} clause.
     */
    @Test
    @Transactional
    void findByPatientIdsSplitsIdListsBeyondTheBindParameterLimit() {
        List<Long> patientIds = Stream.concat(
                LongStream.of(1L, 2L).boxed(),
                LongStream.rangeClosed(1_000L, 100_000L).boxed()
        ).toList();

        Assertions.assertFalse(patientDataEntryAO.findByPatientIds(patientIds).isEmpty(),
                "Entries of the existing patients should still be found");
    }

    @Test
    @Transactional
    void findIdsByCohortIdSplitsExternalIdListsBeyondTheBindParameterLimit() {
        List<String> externalIds = Stream.concat(
                Stream.of("PATIENT_001"),
                LongStream.rangeClosed(1L, 100_000L).mapToObj(i -> "MISSING_" + i)
        ).toList();

        Assertions.assertEquals(List.of(1L), patientAO.findIdsByCohortId(COHORT_ID, externalIds, null));
    }

    @Test
    @Transactional
    void patientReferencesNameTheCohortsPatientsAndRespectTheLimit() {
        Assertions.assertEquals(List.of("PATIENT_001", "PATIENT_002"),
                patientAO.findReferencesByCohortId(COHORT_ID, 100).stream()
                        .map(PatientReferenceDTO::getExternalPatientId)
                        .toList());
        Assertions.assertEquals(1, patientAO.findReferencesByCohortId(COHORT_ID, 1).size());
    }

    @Test
    @Transactional
    void exportWithoutFilterCoversEveryPatientOfTheCohort() {
        List<PatientDataEntryExportDTO> exported = exportBO.getDataForCohort(COHORT_ID, config(null));

        Assertions.assertEquals(Set.of(1L, 2L), patientIdsOf(exported));
    }

    @Test
    @Transactional
    void exportFilteredByExternalPatientIdCoversOnlyThatPatient() {
        PatientExportFilterDTO filter = new PatientExportFilterDTO();
        filter.setExternalPatientIds(List.of(" PATIENT_002 ", "", "UNKNOWN_PATIENT"));

        List<PatientDataEntryExportDTO> exported = exportBO.getDataForCohort(COHORT_ID, config(filter));

        Assertions.assertEquals(Set.of(2L), patientIdsOf(exported));
    }

    @Test
    @Transactional
    void exportLimitCapsTheNumberOfPatients() {
        PatientExportFilterDTO filter = new PatientExportFilterDTO();
        filter.setLimit(1);

        List<PatientDataEntryExportDTO> exported = exportBO.getDataForCohort(COHORT_ID, config(filter));

        Assertions.assertEquals(Set.of(1L), patientIdsOf(exported));
    }

    private static PatientDataExportConfigDTO config(PatientExportFilterDTO filter) {
        PatientDataExportConfigDTO config = new PatientDataExportConfigDTO();
        config.setPatientFilter(filter);
        return config;
    }

    private static Set<Long> patientIdsOf(List<PatientDataEntryExportDTO> exported) {
        return exported.stream().map(PatientDataEntryExportDTO::getPatientId).collect(java.util.stream.Collectors.toSet());
    }
}

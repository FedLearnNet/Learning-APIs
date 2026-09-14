package unit;

import bio.cosy.feddb.core.api.project.SelectedDataIdsDTO;
import bio.cosy.feddb.local.api.cohort.patient.dataentry.PatientDataEntryExportDTO;
import bio.cosy.feddb.local.api.cohort.patient.export.PatientDataExportBO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.*;

import java.util.List;

@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PatientExportTest {

    @Inject
    PatientDataExportBO exportBO;

    @Test
    @Order(1)
    void testPatientExportForQuery() {
        Long queryId = 1L;
        List<SelectedDataIdsDTO> selectedDataIds = List.of(new SelectedDataIdsDTO("2002", "1002")); // Example data IDs


        List<PatientDataEntryExportDTO> export = exportBO.getDataForProject(queryId, selectedDataIds);
        Assertions.assertFalse(export.isEmpty(), "Exported data should not be empty");
    }


}

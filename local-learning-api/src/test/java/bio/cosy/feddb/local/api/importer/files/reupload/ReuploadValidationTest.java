package bio.cosy.feddb.local.api.importer.files.reupload;

import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadInfoDTO;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A reupload is accepted when the new file has the same columns as the one it replaces.
 *
 * <p>The comparison runs between the two files' stored structures. Checking against the connector's
 * configured view instead made an unchanged file fail whenever its columns had been renamed or
 * deselected in the connector, which is the case these tests exist to keep fixed.</p>
 */
@QuarkusTest
class ReuploadValidationTest {

    @Inject
    ReuploadValidationBO reuploadValidationBO;

    @Test
    void acceptsAFileWithTheSameColumns() {
        assertNull(reuploadValidationBO.validate(
                List.of(table("0", "patient_id", "age", "diagnosis")),
                List.of(table("0", "patient_id", "age", "diagnosis"))));
    }

    @Test
    void ignoresCaseAndSurroundingSpace() {
        assertNull(reuploadValidationBO.validate(
                List.of(table("0", "patient_id", "age")),
                List.of(table("0", " Patient_ID ", "AGE"))));
    }

    @Test
    void acceptsAnUnchangedFileEvenWhenTheConnectorRenamedItsColumns() {
        // The stored structure is the source column names; what the connector displays them as does
        // not enter into it.
        ConnectorFileUploadInfoDTO stored = table("0", "patient_id", "age");
        stored.setRenamedColumns(List.of("Patient", "Years"));
        stored.setDeletedColumns(List.of(false, false));

        assertNull(reuploadValidationBO.validate(List.of(stored), List.of(table("0", "patient_id", "age"))));
    }

    @Test
    void acceptsAnUnchangedFileEvenWhenTheConnectorDeselectedAColumn() {
        ConnectorFileUploadInfoDTO stored = table("0", "patient_id", "age", "notes");
        stored.setRenamedColumns(List.of("patient_id", "age", "notes"));
        stored.setDeletedColumns(List.of(false, false, true));

        assertNull(reuploadValidationBO.validate(List.of(stored), List.of(table("0", "patient_id", "age", "notes"))));
    }

    @Test
    void reportsMissingColumnsUnderTheirOriginalNames() {
        var error = reuploadValidationBO.validate(
                List.of(table("0", "patient_id", "age", "Diagnosis")),
                List.of(table("0", "patient_id", "age")));

        assertNotNull(error);
        assertEquals(List.of("Diagnosis"), error.getMissingColumns());
        assertEquals(List.of(), error.getNotFoundColumns());
    }

    @Test
    void reportsColumnsTheReplacementAddedAsNotFound() {
        var error = reuploadValidationBO.validate(
                List.of(table("0", "patient_id", "age")),
                List.of(table("0", "patient_id", "age", "Extra_Column")));

        assertNotNull(error);
        assertEquals(List.of(), error.getMissingColumns());
        assertEquals(List.of("Extra_Column"), error.getNotFoundColumns());
    }

    /**
     * The two lists are complements, not two names for one thing: a column belongs to at most one of
     * them. Reading the report as "these columns are wrong" twice over is what makes an otherwise
     * plain structural diff look like a contradiction.
     */
    @Test
    void reportsEachColumnOnOneSideOnly() {
        var error = reuploadValidationBO.validate(
                List.of(table("0", "patient_id", "race", "gender")),
                List.of(table("0", "patient_id", "ethnicity_code", "sex")));

        assertNotNull(error);
        assertEquals(List.of("race", "gender"), error.getMissingColumns());
        assertEquals(List.of("ethnicity_code", "sex"), error.getNotFoundColumns());
        assertTrue(Collections.disjoint(error.getMissingColumns(), error.getNotFoundColumns()));
    }

    @Test
    void explainsTheMismatchWithoutInternalBaselineTerminology() {
        var error = reuploadValidationBO.validate(
                List.of(table("0", "race")),
                List.of(table("0", "ethnicity_code")));

        assertNotNull(error);
        assertEquals("Uploaded file columns do not match the columns of the file it replaces",
                error.getMessage());
    }

    @Test
    void comparesEveryTableOfAMultiTableFile() {
        assertNull(reuploadValidationBO.validate(
                List.of(table("labs", "patient_id", "value"), table("vitals", "patient_id", "hr")),
                List.of(table("labs", "patient_id", "value"), table("vitals", "patient_id", "hr"))));
    }

    @Test
    void reportsATableMissingFromAMultiTableReplacement() {
        var error = reuploadValidationBO.validate(
                List.of(table("labs", "patient_id", "value"), table("vitals", "patient_id", "hr")),
                List.of(table("labs", "patient_id", "value")));

        assertNotNull(error);
        assertEquals(List.of("hr"), error.getMissingColumns());
    }

    /**
     * Columns shared across tables are prefixed on the way in; that prefix is not part of the name.
     */
    @Test
    void looksPastTheSheetPrefixOnSharedColumns() {
        assertNull(reuploadValidationBO.validate(
                List.of(table("labs", "labs::patient_id", "value"),
                        table("vitals", "vitals::patient_id", "hr")),
                List.of(table("labs", "patient_id", "value"),
                        table("vitals", "patient_id", "hr"))));
    }

    @Test
    void refusesWhenTheStoredStructureIsUnknown() {
        var error = reuploadValidationBO.validate(List.of(), List.of(table("0", "patient_id")));

        assertNotNull(error);
        assertEquals("Could not determine existing file structure", error.getMessage());
    }

    @Test
    void refusesWhenTheReplacementHasNoReadableColumns() {
        var error = reuploadValidationBO.validate(List.of(table("0", "patient_id", "age")), List.of());

        assertNotNull(error);
        assertEquals("Uploaded file contains no readable columns", error.getMessage());
        assertEquals(List.of("patient_id", "age"), error.getMissingColumns());
    }

    @Test
    void acceptsColumnsInADifferentOrder() {
        // A header row's order is not part of what a column is.
        assertNull(reuploadValidationBO.validate(
                List.of(table("0", "patient_id", "age", "diagnosis")),
                List.of(table("0", "diagnosis", "patient_id", "age"))));
    }

    @Test
    void acceptsTablesInADifferentOrder() {
        // An archive is read by several threads at once, so its tables do not even arrive in a
        // fixed order; pairing them by position would refuse a file for being read quickly.
        assertNull(reuploadValidationBO.validate(
                List.of(table("labs", "patient_id", "value"), table("vitals", "patient_id", "hr")),
                List.of(table("vitals", "hr", "patient_id"), table("labs", "value", "patient_id"))));
    }

    @Test
    void acceptsASingleTableFileUnderADifferentName() {
        // One table against one table is that pair whatever the file it came from was called.
        assertNull(reuploadValidationBO.validate(
                List.of(table("admissions", "patient_id", "age")),
                List.of(table("admissions_2026", "patient_id", "age"))));
    }

    @Test
    void saysWhichTableAColumnIsMissingFrom() {
        var error = reuploadValidationBO.validate(
                List.of(table("labs", "patient_id", "value"), table("vitals", "patient_id", "hr")),
                List.of(table("labs", "patient_id"), table("vitals", "patient_id", "hr")));

        assertNotNull(error);
        assertEquals(2, error.getTables().size());
        ReuploadTableDiffDTO labs = tableDiff(error, "labs");
        assertEquals(ReuploadTableDiffDTO.ReuploadTableState.CHANGED, labs.state());
        assertEquals(List.of("value"), labs.missingColumns());
        assertTrue(tableDiff(error, "vitals").matches());
    }

    @Test
    void reportsAWholeTableAsMissingWithEverythingItHeld() {
        var error = reuploadValidationBO.validate(
                List.of(table("labs", "patient_id", "value"), table("vitals", "patient_id", "hr")),
                List.of(table("labs", "patient_id", "value")));

        assertNotNull(error);
        ReuploadTableDiffDTO vitals = tableDiff(error, "vitals");
        assertEquals(ReuploadTableDiffDTO.ReuploadTableState.MISSING, vitals.state());
        assertEquals(List.of("patient_id", "hr"), vitals.missingColumns());
        // patient_id is still in labs, so the file as a whole has not lost it.
        assertEquals(List.of("hr"), error.getMissingColumns());
    }

    @Test
    void reportsATableOnlyTheReplacementHas() {
        var error = reuploadValidationBO.validate(
                List.of(table("labs", "patient_id", "value")),
                List.of(table("labs", "patient_id", "value"), table("notes", "patient_id", "text")));

        assertNotNull(error);
        ReuploadTableDiffDTO notes = tableDiff(error, "notes");
        assertEquals(ReuploadTableDiffDTO.ReuploadTableState.ADDED, notes.state());
        assertEquals(List.of("patient_id", "text"), notes.notFoundColumns());
        assertEquals(List.of("text"), error.getNotFoundColumns());
    }

    @Test
    void stripsTheTablePrefixOnASingleTableFileToo() {
        // The prefix is only added where tables share a name, so it can be on one side and not the
        // other; either way it says which table a column came from rather than what it is called.
        assertNull(reuploadValidationBO.validate(
                List.of(table("labs", "labs::patient_id", "value")),
                List.of(table("labs", "patient_id", "value"))));
    }

    private static ReuploadTableDiffDTO tableDiff(ConnectorFilesReuploadErrorDTO error, String name) {
        return error.getTables().stream()
                .filter(table -> table.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No comparison reported for table " + name));
    }

    private static ConnectorFileUploadInfoDTO table(String sheet, String... columns) {
        ConnectorFileUploadInfoDTO info = new ConnectorFileUploadInfoDTO();
        info.setSheet(sheet);
        info.setColumns(new ArrayList<>(List.of(columns)));
        return info;
    }

}

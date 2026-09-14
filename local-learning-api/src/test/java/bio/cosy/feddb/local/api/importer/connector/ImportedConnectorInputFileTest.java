package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.local.api.importer.connector.input.ConnectorInputConfigDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FTPUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An imported connector configuration brings no file with it.
 *
 * <p>File ids are local to one installation. Exported configurations carry the id the file had
 * where they were made - the shipped US130 site configurations all say {@code fileId: 2} - and
 * adopting it here points the connector at whatever local file happens to be number 2. The
 * connector then reports it already has an input, and the first file the user uploads is validated
 * against that stranger's columns: the reupload is refused as "columns do not match", listing one
 * file's columns as missing and the other's as not found, for a file the user never chose.</p>
 */
class ImportedConnectorInputFileTest {

    @Test
    void dropsTheFileIdAnImportedConfigurationCameWith() {
        ConnectorDTO imported = connectorWithFile(2L);

        ConnectorBO.detachImportedInputFile(imported);

        FileUploadSettingsDTO settings = (FileUploadSettingsDTO) imported.getInputConfig();
        assertNull(settings.getFileId());
        assertNull(settings.getFile());
        assertFalse(settings.isFileExists());
    }

    @Test
    void leavesTheRestOfTheInputConfigurationAlone() {
        ConnectorDTO imported = connectorWithFile(2L);
        FileUploadSettingsDTO settings = (FileUploadSettingsDTO) imported.getInputConfig();
        settings.setDelimiter(";");
        settings.setHasHeader(true);
        settings.setFirstSheetOnly(false);

        ConnectorBO.detachImportedInputFile(imported);

        assertSame(settings, imported.getInputConfig());
        assertEquals(";", settings.getDelimiter());
        assertTrue(settings.isHasHeader());
        assertFalse(settings.getFirstSheetOnly());
    }

    @Test
    void ignoresConnectorsWhoseInputIsNotAFile() {
        ConnectorDTO imported = new ConnectorDTO();
        ConnectorInputConfigDTO other = new FTPUploadSettingsDTO();
        imported.setInputConfig(other);

        ConnectorBO.detachImportedInputFile(imported);

        assertSame(other, imported.getInputConfig());
    }

    @Test
    void toleratesAMissingConnector() {
        ConnectorBO.detachImportedInputFile(null);
    }

    private static ConnectorDTO connectorWithFile(Long fileId) {
        FileUploadSettingsDTO settings = new FileUploadSettingsDTO();
        settings.setFileId(fileId);
        settings.setFile(new byte[]{1, 2, 3});
        settings.setFileExists(true);

        ConnectorDTO connector = new ConnectorDTO();
        connector.setInputConfig(settings);
        return connector;
    }
}

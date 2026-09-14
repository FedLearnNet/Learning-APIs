package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.core.api.file.analytics.FileAnalytics;
import bio.cosy.feddb.local.api.importer.files.table.TableDataPivotBO;
import bio.cosy.feddb.local.api.importer.files.table.TableDataUploadInfoBO;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Wires the file readers the way CDI would, for the tests that exercise them without a container.
 * There is no configuration, so the readers fall back to their defaults: the JVM temp directory for
 * spill files and a single thread per table.
 */
public final class TestReaders {

    private TestReaders() {
    }

    public static TabularFileReaderBO reader() {
        TableDataProfileBO profiles = new TableDataProfileBO(new FileAnalytics());
        TableDataMergeBO merges = new TableDataMergeBO(null);

        DelimitedTableReaderBO delimitedReader = new DelimitedTableReaderBO();
        delimitedReader.profiles = profiles;
        delimitedReader.merges = merges;

        ExcelTableReaderBO excelReader = new ExcelTableReaderBO(null, profiles, merges);

        TabularFileReaderBO reader = new TabularFileReaderBO();
        reader.objectMapper = new ObjectMapper();
        reader.delimitedReader = delimitedReader;
        reader.excelReader = excelReader;
        reader.tableDataPivotBO = new TableDataPivotBO();
        reader.tableDataUploadInfoBO = new TableDataUploadInfoBO();
        reader.profiles = profiles;
        reader.merges = merges;
        return reader;
    }

}

package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.api.file.FileParsingType;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The importer's CSV tokenizer was swapped from Commons CSV to FastCSV for throughput. A tokenizer
 * is only a safe thing to change if it reads the same dialect, and the difference would show up as
 * silently mis-split values in real data rather than as an error - so these compare the two readers
 * field by field on the shapes source extracts actually contain.
 */
class CsvDialectEquivalenceTest {

    static Stream<String> dialects() {
        return Stream.of(
                // plain
                "a,b,c\n1,2,3\n4,5,6\n",
                // quoted fields
                "a,b\n\"hello\",\"world\"\n",
                // delimiter inside quotes
                "a,b\n\"one,two\",three\n",
                // doubled quotes as an escaped quote
                "a,b\n\"say \"\"hi\"\"\",x\n",
                // newline inside a quoted field
                "a,b\n\"line one\nline two\",x\n",
                // empty fields, including a trailing one
                "a,b,c\n,,\n1,,3\n",
                // ragged rows, both short and long
                "a,b,c\n1,2\n1,2,3,4\n",
                // blank lines between records
                "a,b\n\n1,2\n\n\n3,4\n",
                // CRLF line endings
                "a,b\r\n1,2\r\n3,4\r\n",
                // leading and trailing spaces must survive untrimmed
                "a,b\n  padded  , x \n",
                // a lone quote mid-field
                "a,b\n1,he said hi\n",
                // no trailing newline
                "a,b\n1,2",
                // single column
                "only\n1\n2\n"
        );
    }

    @ParameterizedTest
    @MethodSource("dialects")
    void splitsFieldsExactlyAsCommonsCsvDid(String content) throws Exception {
        assertEquals(commonsCsvFields(content, ','), readerFields(content, ","),
                () -> "for input " + content.replace("\n", "\\n").replace("\r", "\\r"));
    }

    @ParameterizedTest
    @MethodSource("dialects")
    void handlesOtherDelimitersTheSameWay(String content) throws Exception {
        String semicolons = content.replace(',', ';');
        assertEquals(commonsCsvFields(semicolons, ';'), readerFields(semicolons, ";"),
                () -> "for input " + semicolons.replace("\n", "\\n"));
    }

    /** Header handling goes through a different call path, so it gets its own check. */
    @Test
    void readsHeadersAndRowsThroughTheImporterItself() throws Exception {
        Path csv = Files.createTempFile("dialect", ".csv");
        Files.writeString(csv, "a,\"b,with comma\",c\n1,\"say \"\"hi\"\"\",3\n4,5,6\n",
                StandardCharsets.UTF_8);

        TableData table = TestReaders.reader()
                .getFirstTableData(csv.toFile(), TableReadSpec.whole(csvSettings()), null, null, null);

        assertNotNull(table);
        assertEquals(List.of("a", "b,with comma", "c"), table.getColumns());
        List<Map<String, Object>> rows = table.getRows();
        assertEquals(2, rows.size());
        assertEquals("say \"hi\"", rows.getFirst().get("b,with comma"));
        assertEquals("6", rows.get(1).get("c"));
        table.close();
        Files.deleteIfExists(csv);
    }

    /** What the importer's reader produces, via the same configuration the readers use. */
    private static List<List<String>> readerFields(String content, String delimiter) throws Exception {
        List<List<String>> records = new ArrayList<>();
        try (java.io.Reader in = new StringReader(content);
             de.siegmar.fastcsv.reader.CsvReader<de.siegmar.fastcsv.reader.CsvRecord> csv =
                     CsvDialect.reader(in, delimiter)) {
            for (de.siegmar.fastcsv.reader.CsvRecord record : csv) {
                records.add(List.copyOf(record.getFields()));
            }
        }
        return records;
    }

    private static List<List<String>> commonsCsvFields(String content, char delimiter) throws IOException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setSkipHeaderRecord(false)
                .get();
        List<List<String>> records = new ArrayList<>();
        try (StringReader in = new StringReader(content);
             CSVParser parser = CSVParser.builder().setReader(in).setFormat(format).get()) {
            for (CSVRecord record : parser) {
                List<String> fields = new ArrayList<>(record.size());
                for (int i = 0; i < record.size(); i++) {
                    fields.add(record.get(i));
                }
                records.add(fields);
            }
        }
        return records;
    }

    private static FileParsingSettingsDTO csvSettings() {
        FileParsingSettingsDTO settings = new FileParsingSettingsDTO();
        settings.setFileType(FileParsingType.CSV);
        settings.setHasHeader(true);
        settings.setFirstSheetOnly(true);
        settings.setDelimiter(",");
        return settings;
    }

}

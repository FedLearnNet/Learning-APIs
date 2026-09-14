package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.table.ColumnNames;
import de.siegmar.fastcsv.reader.CommentStrategy;
import de.siegmar.fastcsv.reader.CsvReader;
import de.siegmar.fastcsv.reader.CsvRecord;

import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CsvDialect {

    private static final Set<String> SUPPORTED_EXTENSIONS =
            Set.of(".csv", ".tsv", ".txt", ".psv", ".dat", ".tab", ".dsv");

    public static CsvReader<CsvRecord> reader(Reader reader, String delimiter) {
        return CsvReader.builder()
                .fieldSeparator(delimiter.charAt(0))
                .quoteCharacter('"')
                .commentStrategy(CommentStrategy.NONE)
                .skipEmptyLines(true)
                .allowExtraFields(true)
                .allowMissingFields(true)
                .allowExtraCharsAfterClosingQuote(true)
                .ofCsvRecord(reader);
    }

    public static String delimiter(FileParsingSettingsDTO settings) {
        if (settings.getCustomDelimiter() != null && !settings.getCustomDelimiter().isBlank()) {
            return settings.getCustomDelimiter();
        }
        if (settings.getDelimiter() == null || settings.getDelimiter().isBlank()) {
            return ",";
        }
        return switch (settings.getDelimiter()) {
            case "\\t", "\t" -> "\t";
            case "CUSTOM" -> settings.getCustomDelimiter() != null ? settings.getCustomDelimiter() : ",";
            default -> settings.getDelimiter();
        };
    }

    public static List<String> columns(CsvRecord firstRecord, boolean hasHeader) {
        List<String> columns = new ArrayList<>(firstRecord.getFieldCount());
        for (int index = 0; index < firstRecord.getFieldCount(); index++) {
            columns.add(hasHeader
                    ? ColumnNames.column(firstRecord.getField(index))
                    : String.valueOf(index));
        }
        return columns;
    }

    public static Object value(CsvRecord record, int columnIndex) {
        return columnIndex >= 0 && columnIndex < record.getFieldCount()
                ? record.getField(columnIndex)
                : null;
    }

    public static boolean isSupportedEntry(String entryName) {
        String name = entryName.toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(name::endsWith);
    }
}

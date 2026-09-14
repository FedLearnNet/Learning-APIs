package bio.cosy.feddb.local.api.importer.files.read;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;

/**
 * What one read of a file asks for: how the file is laid out, how many rows are wanted, what the
 * result is for, and which import it belongs to.
 *
 * <p>These travel together through every reader, and they decide each other: a bounded read stays on
 * the heap, an unbounded one spills to disk, and anything bounded or destined for a preview is
 * profiled while it is parsed. As one value those rules are stated once; as four parameters they were
 * restated - and could disagree - at every call. {@link #importId()} rides along for the same reason:
 * the reader opening the twentieth entry of an archive is the only place that knows it, and the spec
 * is what already reaches it.</p>
 *
 * <p>Use the factory methods rather than the constructor: they are what derives {@link #profile()}.</p>
 *
 * @param maxRows  rows to read per table, or {@code null} for all of them
 * @param preview  whether the result is shown to a user rather than fed to a run
 * @param profile  whether column statistics are gathered while the rows are read
 * @param importId the import to report tables to, or {@code null} when nobody is watching
 */
public record TableReadSpec(
        FileParsingSettingsDTO settings,
        Integer maxRows,
        boolean preview,
        boolean profile,
        String importId
) {

    public static TableReadSpec of(FileParsingSettingsDTO settings, Integer maxRows, boolean preview) {
        return new TableReadSpec(settings, maxRows, preview, maxRows != null || preview, null);
    }

    /** Everything the file has, unprofiled: what a run reads. */
    public static TableReadSpec whole(FileParsingSettingsDTO settings) {
        return of(settings, null, false);
    }

    /**
     * How an upload analysis reads the file. Statistics describe the whole file, so asking for them
     * is also what stops the read being cut short at the rows the preview shows.
     */
    public static TableReadSpec forAnalysis(
            FileParsingSettingsDTO settings,
            Integer sampleRows,
            boolean preview,
            boolean statistics
    ) {
        return of(settings, statistics ? null : sampleRows, preview);
    }

    /** The same read without a row limit, for a step that has to see the whole table first. */
    public TableReadSpec unbounded() {
        return new TableReadSpec(settings, null, false, false, importId);
    }

    /** The same read without statistics, for tables whose profile is taken from what they become. */
    public TableReadSpec unprofiled() {
        return new TableReadSpec(settings, maxRows, preview, false, importId);
    }

    /** The same read, reported to whoever is watching the import it belongs to. */
    public TableReadSpec forImport(String id) {
        return new TableReadSpec(settings, maxRows, preview, profile, id);
    }

    public String delimiter() {
        return CsvDialect.delimiter(settings);
    }

    public boolean hasHeader() {
        return settings.isHasHeader();
    }

    /** Whether all of the file's tables are wanted rather than only its first. */
    public boolean allTables() {
        return Boolean.FALSE.equals(settings.getFirstSheetOnly());
    }

    /** Whether {@code rowsRead} has reached the requested limit. */
    public boolean isFull(long rowsRead) {
        return maxRows != null && rowsRead >= maxRows;
    }
}

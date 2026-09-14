package bio.cosy.feddb.core.api.file.analytics;

public enum FileDataType {
    HTML,
    CSV,
    TSV,
    JSON,
    IMAGE,
    TEXT,
    UNKNOWN;

    private static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".bmp"};
    private static final String[] TEXT_EXTENSIONS = {".txt", ".log", ".md"};

    public static FileDataType fromFilename(String filename) {
        if (filename == null) return UNKNOWN;
        String lowerFilename = filename.toLowerCase();

        if (lowerFilename.endsWith(".html")) return HTML;
        if (lowerFilename.endsWith(".csv")) return CSV;
        if (lowerFilename.endsWith(".tsv")) return TSV;
        if (lowerFilename.endsWith(".json")) return JSON;

        for (String ext : IMAGE_EXTENSIONS) {
            if (lowerFilename.endsWith(ext)) return IMAGE;
        }

        for (String ext : TEXT_EXTENSIONS) {
            if (lowerFilename.endsWith(ext)) return TEXT;
        }

        return UNKNOWN;
    }

    public String getMineType() {
        return switch (this) {
            case HTML -> "text/html";
            case CSV -> "text/csv";
            case TSV -> "text/tab-separated-values";
            case JSON -> "application/json";
            case IMAGE -> "image/*"; // Generic image MIME type
            case TEXT -> "text/plain";
            default -> "application/octet-stream"; // Default for unknown types
        };
    }

}

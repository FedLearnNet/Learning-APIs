package bio.cosy.feddb.core.api.app.config;

public enum ToolConfigDataType {
    HTML,
    CSV,
    TSV,
    JSON,
    IMAGE,
    TEXT,
    STRING,
    PATH,
    UNKNOWN;

    private static final String[] IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".gif", ".bmp"};
    private static final String[] TEXT_EXTENSIONS = {".txt", ".log", ".md"};

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

    public static ToolConfigDataType fromMimeType(String mimeType) {
        if (mimeType == null) return UNKNOWN;
        mimeType = mimeType.toLowerCase();
        return switch (mimeType) {
            case "text/html" -> HTML;
            case "text/csv" -> CSV;
            case "text/tab-separated-values" -> TSV;
            case "application/json" -> JSON;
            case "image/jpeg", "image/png", "image/gif", "image/bmp" -> IMAGE;
            case "text/plain" -> TEXT;
            default -> PATH;
        };
    }

}

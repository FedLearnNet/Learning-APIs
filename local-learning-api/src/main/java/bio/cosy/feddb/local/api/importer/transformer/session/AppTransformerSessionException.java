package bio.cosy.feddb.local.api.importer.transformer.session;

public class AppTransformerSessionException extends RuntimeException {

    public AppTransformerSessionException(String message) {
        super(message);
    }

    public AppTransformerSessionException(String message, Throwable cause) {
        super(message, cause);
    }
}

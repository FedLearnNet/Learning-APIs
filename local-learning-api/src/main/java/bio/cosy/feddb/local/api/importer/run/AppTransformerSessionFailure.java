package bio.cosy.feddb.local.api.importer.run;

public class AppTransformerSessionFailure extends RuntimeException {

    public AppTransformerSessionFailure(String message) {
        super(message);
    }

    public AppTransformerSessionFailure(String message, Throwable cause) {
        super(message, cause);
    }
}

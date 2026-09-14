package bio.cosy.feddb.local.api.importer.validation;

/**
 * Reasons a recomputed column category set may be unreliable for mapping/validation.
 */
public enum PreviewValidationWarningType {
    /** Column is produced/overwritten by a transformer and also used as a mapping selector / one-hot key. */
    TRANSFORMED_MAPPING_KEY,
    /** Column is referenced by more than one mapping key. */
    MULTIPLE_MAPPING_KEYS,
    /** Column is produced by a remote app transformer, so its categories cannot be computed locally. */
    APP_TRANSFORMER_OUTPUT
}

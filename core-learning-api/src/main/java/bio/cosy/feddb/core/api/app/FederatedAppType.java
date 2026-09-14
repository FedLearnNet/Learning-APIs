package bio.cosy.feddb.core.api.app;

import lombok.Getter;

@Getter
public enum FederatedAppType {
    PRE_PROCESSING,
    ANALYSIS,
    POST_PROCESSING,
    EVALUATION,
    SELF_LEARNED,
    DATA_TRANSFORMATION,
    EXTRACTOR,
    EXPORT
}

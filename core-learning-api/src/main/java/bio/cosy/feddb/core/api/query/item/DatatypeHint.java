package bio.cosy.feddb.core.api.query.item;

// reads the value as a number, also when stored as text; INTEGER only parses whole numbers
public enum DatatypeHint {
    INTEGER,
    FLOAT
}

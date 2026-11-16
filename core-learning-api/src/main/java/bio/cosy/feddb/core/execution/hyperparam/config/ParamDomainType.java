package bio.cosy.feddb.core.execution.hyperparam.config;

public enum ParamDomainType {
    FIXED, //One value, the parameter is not searched
    CHOICE, //One of the listed values
    INT_RANGE, //Whole numbers from low to high, both inclusive
    UNIFORM, //Real numbers drawn evenly from low (inclusive) to high (exclusive)
    LOG_UNIFORM //Positive real numbers drawn evenly on a log scale, e.g. a learning rate
}

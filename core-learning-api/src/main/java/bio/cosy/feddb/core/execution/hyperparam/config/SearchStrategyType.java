package bio.cosy.feddb.core.execution.hyperparam.config;

public enum SearchStrategyType {
    GRID, //Every combination of a finite space
    RANDOM //A fixed number of seeded draws, without replacement on a finite space
}

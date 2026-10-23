package bio.cosy.feddb.core.execution.event;


public final class ExecutionChannels {

    //A whole experiment run was started, each run has multiple steps
    public static final String RUN_STARTED = "execution-run-started";

    //A Step in a run/experiment has changed
    public static final String STEP_CHANGED = "execution-step-changed";

    // Each step has one to n step, and one of them i started
    public static final String ATTEMPT_LAUNCH = "execution-attempt-launch";

    // An attempt reached a final status, its container can be stopped and its api key revoked
    public static final String ATTEMPT_FINISHED = "execution-attempt-finished";

    //a whole step is finished
    public static final String STEP_FINISHED = "execution-step-finished";

    // a whole run is finished
    public static final String RUN_FINISHED = "execution-run-finished";
}

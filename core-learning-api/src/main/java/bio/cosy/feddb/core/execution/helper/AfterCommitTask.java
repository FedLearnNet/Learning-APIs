package bio.cosy.feddb.core.execution.helper;

import io.quarkus.logging.Log;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;

public class AfterCommitTask implements Synchronization {

    private final String name;
    private final Runnable task;

    public AfterCommitTask(String name, Runnable task) {
        this.name = name;
        this.task = task;
    }

    @Override
    public void beforeCompletion() {
    }

    // A failing task cannot undo the commit, so it is only logged.
    @Override
    public void afterCompletion(int status) {
        if (status != Status.STATUS_COMMITTED) {
            Log.debugf("%s dropped, its transaction did not commit", name);
            return;
        }
        try {
            task.run();
        } catch (RuntimeException e) {
            Log.errorf(e, "%s failed after commit", name);
        }
    }
}

package bio.cosy.feddb.core.execution.helper;

import lombok.Data;

import java.util.concurrent.TimeUnit;

@Data
public class ExecutionSessionProgress {
    private final long started = System.nanoTime();
    private volatile boolean waited;
    private volatile long nextLog;

    public long elapsedSeconds(long now) {
        return TimeUnit.NANOSECONDS.toSeconds(now - started);
    }
}

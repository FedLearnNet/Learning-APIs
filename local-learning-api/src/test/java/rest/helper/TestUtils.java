package rest.helper;

import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;

import java.time.Duration;

public class TestUtils {
    private static final Logger LOG = Logger.getLogger(TestUtils.class);

    /**
     * Waits for the given duration without blocking the calling thread.
     *
     * @param duration how long to wait
     * @return a Uni<Void> that completes after the delay
     */
    public static Uni<Void> waitNonBlocking(Duration duration) {
        LOG.infof("Waiting non-blocking for %s...", duration);
        return Uni.createFrom().voidItem()
                // delay without blocking
                .onItem().delayIt().by(duration)
                // invoked when the Uni completes
                .invoke(() -> LOG.infof("Waited %s", duration));
    }
}

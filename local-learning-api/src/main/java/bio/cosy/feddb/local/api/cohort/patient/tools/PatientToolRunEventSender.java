package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.api.run.message.RunMessageLogDTO;
import io.quarkus.logging.Log;
import io.smallrye.reactive.messaging.annotations.Broadcast;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;

import java.util.List;

/**
 * Publishes what happens to patient tool runs - new log lines and status changes - on an
 * in-memory channel, which the progress streams of open export dialogs listen to.
 */
@ApplicationScoped
public class PatientToolRunEventSender {
    public static final String PATIENT_TOOL_RUN_CHANNEL = "patient-tool-run-events";

    @Inject
    @Channel(PATIENT_TOOL_RUN_CHANNEL)
    @OnOverflow(OnOverflow.Strategy.DROP)
    @Broadcast
    Emitter<PatientToolRunProgressDTO> emitter;

    @Inject
    PatientToolRunMapper mapper;

    public void sendLog(Long runId, RunMessageLogDTO log) {
        if (runId == null || log == null) {
            return;
        }
        send(mapper.toProgressDto(runId, null, List.of(log), List.of(), false));
    }

    public void sendStatus(PatientToolRunStatusDTO status) {
        if (status == null || status.getRunId() == null) {
            return;
        }
        send(mapper.toProgressDto(status.getRunId(), status, List.of(), List.of(), false));
    }

    public void send(PatientToolRunProgressDTO event) {
        if (event == null || event.getRunId() == null) {
            return;
        }
        try {
            emitter.send(event);
        } catch (Exception e) {
            // Nobody listening or the buffer is full; the streams catch up from the database
            Log.debugf("Could not publish an event of patient tool run %d: %s", event.getRunId(), e.getMessage());
        }
    }
}

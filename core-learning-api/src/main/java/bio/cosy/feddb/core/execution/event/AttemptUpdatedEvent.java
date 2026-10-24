package bio.cosy.feddb.core.execution.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// What an app reported changed its attempt, e.g. status, progress or a log; only observed, never on a channel.
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AttemptUpdatedEvent {
    private Long attemptId;
}

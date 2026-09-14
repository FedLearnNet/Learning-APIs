package bio.cosy.feddb.core.api.run;

import lombok.Data;

@Data
public class FinishRunDTO {
    private Long runId;

    // Wrapper-measured run metadata (timings today, extensible later). RUNTIME in meta.timings is
    // always reported; the OVERHEAD_* entries are sent by the wrapper but only persisted/exposed
    // when posymed.runtime.overhead.enabled is true. Null/absent for historical or ERROR runs.
    private RunMetaDTO meta;

    // An app may finish with an error (e.g. FINISH_FEDERATED_RUN status=ERROR). Capture it so the step
    // is marked ERROR (not silently FINISHED) and the failure is persisted and reported to global.
    private RunStatusTypes status;
    private String error;
}

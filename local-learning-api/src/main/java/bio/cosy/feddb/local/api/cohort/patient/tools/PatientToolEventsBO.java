package bio.cosy.feddb.local.api.cohort.patient.tools;

import bio.cosy.feddb.core.execution.event.RunFinishedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/** What happens to the runs of patient tools. */
@ApplicationScoped
public class PatientToolEventsBO {

    @Inject
    PatientToolBO patientToolBO;

    void onRunFinished(@Observes(during = TransactionPhase.AFTER_SUCCESS) RunFinishedEvent event) {
        patientToolBO.discardInput(event.getRunId());
    }
}

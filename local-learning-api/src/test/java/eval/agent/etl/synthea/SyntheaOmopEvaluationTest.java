package eval.agent.etl.synthea;

import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerContainerBO;
import bio.cosy.feddb.local.api.importer.transformer.session.AppTransformerSessionBO;
import eval.agent.etl.gdc.pipeline.MatchingBenchmarkRunner;
import eval.agent.etl.transform.setting.TransformerWriterEvaluationProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Maps the Synthea tables of OMAP onto the OMOP CDM schema like a user of the assistant, and scores the fields against
 * the ground truth (see GdcReport). Needs the imported OMOP schema (-Deval.synthea.schema-id) and the tables
 * (download-synthea-omop.sh).
 */
@QuarkusTest
@TestProfile(TransformerWriterEvaluationProfile.class)
public class SyntheaOmopEvaluationTest {

    @Inject
    MatchingBenchmarkRunner runner;

    @Inject
    CodeTransformerContainerBO codeRunner;

    @Inject
    AppTransformerSessionBO appSessions;

    @Test
    void matchTheTablesOntoOmop() throws IOException {
        runner.run(SyntheaOmopTables.BENCHMARK, SyntheaOmopTables.schemaId(), SyntheaOmopTables.selected());
    }

    @AfterEach
    void stopContainers() {
        codeRunner.stop();
        appSessions.closeAll();
    }
}

package eval.agent.etl.gdc;

import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerContainerBO;
import bio.cosy.feddb.local.api.importer.transformer.session.AppTransformerSessionBO;
import eval.agent.etl.gdc.pipeline.MatchingBenchmarkRunner;
import eval.agent.etl.gdc.setting.GdcStudies;
import eval.agent.etl.transform.setting.TransformerWriterEvaluationProfile;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

/**
 * Maps the studies of the GDC schema matching benchmark onto the GDC schema like a user of the assistant, and scores
 * the fields against the ground truth (see GdcReport). Needs the uploaded GDC schema (-Deval.gdc.schema-id) and the
 * study tables (download-gdc-sm.sh).
 */
@QuarkusTest
@TestProfile(TransformerWriterEvaluationProfile.class)
public class GdcBenchmarkEvaluationTest {

    @Inject
    MatchingBenchmarkRunner runner;

    @Inject
    CodeTransformerContainerBO codeRunner;

    @Inject
    AppTransformerSessionBO appSessions;

    @Test
    void matchTheStudiesOntoGdc() throws IOException {
        runner.run(GdcStudies.BENCHMARK, GdcStudies.schemaId(), GdcStudies.selected());
    }

    @AfterEach
    void stopContainers() {
        codeRunner.stop();
        appSessions.closeAll();
    }
}

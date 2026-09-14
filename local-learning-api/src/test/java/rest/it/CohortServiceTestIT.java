package rest.it;

import io.quarkus.test.junit.QuarkusIntegrationTest;
import rest.CohortServiceE2ETest;

@QuarkusIntegrationTest
class CohortServiceTestIT extends CohortServiceE2ETest {
    // Execute the same tests but in packaged mode.
}

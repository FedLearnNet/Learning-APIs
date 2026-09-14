package bio.cosy.feddb.core.unit;

//@QuarkusTest
class FederatedTestRunBOTest {
/*
    @Inject
    FederatedTestRunBO federatedTestRunBO;

    @Inject
    FederatedTestRunAO federatedTestRunAO;

    @Inject
    FederatedAppAO federatedAppAO;

    @Inject
    FederatedAppVersionAO federatedAppVersionAO;

    @Test
    @TestTransaction
    void updateRunKeepsExistingAppVersionManaged() {
        FederatedAppEntity app = new FederatedAppEntity();
        app.setName("Federated Run Test App");
        app.setSlug("federated-run-test-" + UUID.randomUUID());
        app.setType(FederatedAppType.ANALYSIS);
        federatedAppAO.persist(app);

        FederatedAppVersionEntity appVersion = new FederatedAppVersionEntity();
        appVersion.setFederatedApp(app);
        appVersion.setMajorVersion("1.0.0");
        appVersion.setMinorVersion("1.0.0");
        appVersion.setPatchVersion("1.0.0");
        federatedAppVersionAO.persist(appVersion);

        FederatedTestRunEntity run = new FederatedTestRunEntity();
        run.setFederatedAppVersion(appVersion);
        run.setStatus(RunStatusTypes.PENDING);
        run.setCurrentRound(0);
        run.setTotalRounds(3);
        run.setStartAggregator(true);
        federatedTestRunAO.persist(run);
        federatedTestRunAO.flush();

        FederatedTestRunDTO update = new FederatedTestRunDTO();
        update.setId(run.getId());
        update.setVersion(run.getVersion());
        update.setFederatedAppVersionId(appVersion.getId());
        update.setStatus(RunStatusTypes.FINISHED);
        update.setCurrentRound(3);
        update.setAggregatorId("aggregator-1");

        FederatedTestRunDTO updated = federatedTestRunBO.updateRun(update);

        assertEquals(3, updated.getCurrentRound());
        assertEquals("aggregator-1", updated.getAggregatorId());
        assertEquals(appVersion.getId(), updated.getFederatedAppVersionId());
    }*/
}

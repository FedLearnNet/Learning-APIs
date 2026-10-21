package de.unihamburg.daibetes.api.project.experiment.federated.aggregator;

import bio.cosy.feddb.core.api.app.FederatedAppDetailDTO;
import bio.cosy.feddb.core.api.socket.FederatedLearningRelayInfoDTO;
import bio.cosy.feddb.core.api.workflow.node.WorkflowNodeDetailDTO;
import bio.cosy.feddb.core.security.Scope;
import bio.cosy.feddb.core.security.ToolApiKeyService;
import bio.cosy.feddb.core.services.controller.ControllerStartLearningRequestDTO;
import bio.cosy.feddb.core.services.controller.ControllerStartLearningResponseDTO;
import bio.cosy.feddb.core.services.controller.ControllerStartRelayingRequestDTO;
import bio.cosy.feddb.core.services.controller.RelaySignCertRequestDTO;
import bio.cosy.feddb.core.services.controller.RelaySignCertResponseDTO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentBO;
import de.unihamburg.daibetes.api.project.experiment.federated.ProjectFederatedExperimentEntity;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepBO;
import de.unihamburg.daibetes.api.project.experiment.federated.step.ProjectFederatedExperimentStepEntity;
import de.unihamburg.daibetes.api.workflow.WorkflowBO;
import de.unihamburg.daibetes.services.GlobalRelayService;
import de.unihamburg.daibetes.services.LocalControllerLearningService;
import de.unihamburg.daibetes.services.WorkflowOrchestratorBO;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProjectFederatedExperimentAggregatorBOTest {

    private static final Long STEP_ID = 42L;
    private static final String CLIENT_ID = "relay-client-1";
    private static final String CHANNEL = "channel-1";
    private static final String API_KEY = "api-key-1";
    private static final String CSR = "-----BEGIN CERTIFICATE REQUEST-----";
    private static final String CERTIFICATE = "-----BEGIN CERTIFICATE-----";
    private static final String CONTAINER_ID = "container-1";

    private ProjectFederatedExperimentAggregatorBO aggregatorBO() {
        ProjectFederatedExperimentAggregatorBO bo = new ProjectFederatedExperimentAggregatorBO();
        bo.workflowOrchestratorBO = mock(WorkflowOrchestratorBO.class);
        bo.toolApiKeyService = mock(ToolApiKeyService.class);
        bo.stepBO = mock(ProjectFederatedExperimentStepBO.class);
        bo.workflowBO = mock(WorkflowBO.class);
        bo.experimentBO = mock(ProjectFederatedExperimentBO.class);
        bo.controllerService = mock(LocalControllerLearningService.class);
        bo.globalRelayService = mock(GlobalRelayService.class);
        when(bo.toolApiKeyService.issue(Scope.FEDERATED_AGGREGATOR_RUN, STEP_ID)).thenReturn(API_KEY);
        return bo;
    }

    private FederatedLearningRelayInfoDTO relayInfo() {
        FederatedLearningRelayInfoDTO relay = new FederatedLearningRelayInfoDTO();
        relay.setId(CLIENT_ID);
        relay.setKey("client-key");
        relay.setChannel(CHANNEL);
        relay.setRelayKey("relay-key");
        relay.setCoordinatorId(CLIENT_ID);
        relay.setCoordinator(true);
        relay.setMaxNumClients(3);
        return relay;
    }

    private ProjectFederatedExperimentEntity experiment() {
        ProjectFederatedExperimentStepEntity step = new ProjectFederatedExperimentStepEntity();
        step.setId(STEP_ID);
        ProjectFederatedExperimentEntity experiment = new ProjectFederatedExperimentEntity();
        experiment.setId(1L);
        experiment.setCurrentWorkflowNode(step);
        return experiment;
    }

    private WorkflowNodeDetailDTO node() {
        WorkflowNodeDetailDTO node = new WorkflowNodeDetailDTO();
        node.setId(STEP_ID);
        node.setWorkflowId(1L);
        node.setExecutionOrder(0);
        FederatedAppDetailDTO app = new FederatedAppDetailDTO();
        app.setImageName("some-image:latest");
        node.setAppDetail(app);
        return node;
    }

    private Response okStartLearningResponse(String csr) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(Response.Status.OK.getStatusCode());
        when(response.readEntity(ControllerStartLearningResponseDTO.class))
                .thenReturn(new ControllerStartLearningResponseDTO(csr));
        return response;
    }

    private Response statusResponse(int status) {
        Response response = mock(Response.class);
        when(response.getStatus()).thenReturn(status);
        when(response.readEntity(String.class)).thenReturn("error body");
        return response;
    }

    @Test
    void happyPathRegistersWithControllerBeforeStartingTheContainer() {
        ProjectFederatedExperimentAggregatorBO bo = aggregatorBO();
        FederatedLearningRelayInfoDTO relay = relayInfo();
        Response startLearningResponse = okStartLearningResponse(CSR);
        Response startRelayingResponse = statusResponse(Response.Status.OK.getStatusCode());
        when(bo.controllerService.startLearning(any())).thenReturn(startLearningResponse);
        when(bo.globalRelayService.signCert(any())).thenReturn(new RelaySignCertResponseDTO(CERTIFICATE));
        when(bo.controllerService.startRelaying(any())).thenReturn(startRelayingResponse);
        when(bo.workflowOrchestratorBO.executeWorkflow(any(), eq(ProjectFederatedExperimentAggregatorBO.WS_BASE_URL)))
                .thenReturn(CONTAINER_ID);

        bo.start(experiment(), node(), relay);

        ControllerStartLearningRequestDTO expectedStart = new ControllerStartLearningRequestDTO(
                CHANNEL, CLIENT_ID, "client-key", "relay-key", STEP_ID.toString(), CLIENT_ID, 3, null, CLIENT_ID, null);
        verify(bo.controllerService).startLearning(expectedStart);
        verify(bo.globalRelayService).signCert(new RelaySignCertRequestDTO(CHANNEL, CLIENT_ID, CSR));
        verify(bo.controllerService).startRelaying(new ControllerStartRelayingRequestDTO(CHANNEL, CLIENT_ID, CERTIFICATE));
        verify(bo.workflowOrchestratorBO).executeWorkflow(any(), eq(ProjectFederatedExperimentAggregatorBO.WS_BASE_URL));
        verify(bo.stepBO).setPlatformAggregatorContainer(STEP_ID, CONTAINER_ID, relay);
    }

    @Test
    void nonOkStartLearningResponseAbortsAndRevokesTheApiKey() {
        ProjectFederatedExperimentAggregatorBO bo = aggregatorBO();
        Response startLearningResponse = statusResponse(Response.Status.BAD_REQUEST.getStatusCode());
        when(bo.controllerService.startLearning(any())).thenReturn(startLearningResponse);

        assertThrows(IllegalStateException.class, () -> bo.start(experiment(), node(), relayInfo()));

        verify(bo.toolApiKeyService).revoke(Scope.FEDERATED_AGGREGATOR_RUN, STEP_ID);
        verifyNoInteractions(bo.globalRelayService);
        verify(bo.workflowOrchestratorBO, never()).executeWorkflow(any(), any());
    }

    @Test
    void blankCsrInStartLearningResponseAbortsAndRevokesTheApiKey() {
        ProjectFederatedExperimentAggregatorBO bo = aggregatorBO();
        Response startLearningResponse = okStartLearningResponse(" ");
        when(bo.controllerService.startLearning(any())).thenReturn(startLearningResponse);

        assertThrows(IllegalStateException.class, () -> bo.start(experiment(), node(), relayInfo()));

        verify(bo.toolApiKeyService).revoke(Scope.FEDERATED_AGGREGATOR_RUN, STEP_ID);
        verifyNoInteractions(bo.globalRelayService);
        verify(bo.workflowOrchestratorBO, never()).executeWorkflow(any(), any());
    }

    @Test
    void blankCertificateFromRelayAbortsAndRevokesTheApiKey() {
        ProjectFederatedExperimentAggregatorBO bo = aggregatorBO();
        Response startLearningResponse = okStartLearningResponse(CSR);
        when(bo.controllerService.startLearning(any())).thenReturn(startLearningResponse);
        when(bo.globalRelayService.signCert(any())).thenReturn(new RelaySignCertResponseDTO(" "));

        assertThrows(IllegalStateException.class, () -> bo.start(experiment(), node(), relayInfo()));

        verify(bo.toolApiKeyService).revoke(Scope.FEDERATED_AGGREGATOR_RUN, STEP_ID);
        verify(bo.controllerService, never()).startRelaying(any());
        verify(bo.workflowOrchestratorBO, never()).executeWorkflow(any(), any());
    }

    @Test
    void nonOkStartRelayingResponseAbortsAndRevokesTheApiKey() {
        ProjectFederatedExperimentAggregatorBO bo = aggregatorBO();
        Response startLearningResponse = okStartLearningResponse(CSR);
        Response startRelayingResponse = statusResponse(Response.Status.FORBIDDEN.getStatusCode());
        when(bo.controllerService.startLearning(any())).thenReturn(startLearningResponse);
        when(bo.globalRelayService.signCert(any())).thenReturn(new RelaySignCertResponseDTO(CERTIFICATE));
        when(bo.controllerService.startRelaying(any())).thenReturn(startRelayingResponse);

        assertThrows(IllegalStateException.class, () -> bo.start(experiment(), node(), relayInfo()));

        verify(bo.toolApiKeyService).revoke(Scope.FEDERATED_AGGREGATOR_RUN, STEP_ID);
        verify(bo.workflowOrchestratorBO, never()).executeWorkflow(any(), any());
    }
}

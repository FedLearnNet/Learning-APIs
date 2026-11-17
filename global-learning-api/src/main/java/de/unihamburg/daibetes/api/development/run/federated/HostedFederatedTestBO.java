package de.unihamburg.daibetes.api.development.run.federated;

import bio.cosy.feddb.core.execution.ExecutionLocation;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.event.ExecutionEventPublisher;
import bio.cosy.feddb.core.execution.message.ExecutionMessageRoundDTO;
import bio.cosy.feddb.core.execution.run.dto.AppMessageWrapperDTO;
import bio.cosy.feddb.core.execution.run.dto.FederatedRunConfigDTO;
import bio.cosy.feddb.core.execution.run.dto.StartRunDTO;
import bio.cosy.feddb.core.execution.run.types.*;
import bio.cosy.feddb.core.services.controller.*;
import de.unihamburg.daibetes.api.app.version.FederatedAppVersionEntity;
import de.unihamburg.daibetes.api.development.engine.TestEngineMode;
import de.unihamburg.daibetes.api.development.engine.TestEngineRunBO;
import de.unihamburg.daibetes.api.development.run.TestRunBO;
import de.unihamburg.daibetes.api.development.run.TestRunCleanupBO;
import de.unihamburg.daibetes.api.development.testembed.TestEmbedConnections;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptAO;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptEntity;
import de.unihamburg.daibetes.execution.attempt.ExecutionAttemptLifecycle;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationEntity;
import de.unihamburg.daibetes.execution.federation.ExecutionFederationMapper;
import de.unihamburg.daibetes.execution.federation.ExecutionParticipantEntity;
import de.unihamburg.daibetes.execution.run.ExecutionRunAO;
import de.unihamburg.daibetes.execution.run.ExecutionRunEntity;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepAO;
import de.unihamburg.daibetes.execution.step.ExecutionRunStepEntity;
import de.unihamburg.daibetes.services.GlobalRelayService;
import de.unihamburg.daibetes.services.LocalControllerLearningService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class HostedFederatedTestBO {

    private static final String NOT_HOSTED = "Not a hosted federated test";

    @Inject
    TestRunBO testRunBO;

    @Inject
    TestRunCleanupBO cleanupBO;

    @Inject
    ExecutionRunAO runAO;

    @Inject
    ExecutionRunStepAO stepAO;

    @Inject
    ExecutionAttemptAO attemptAO;

    @Inject
    ExecutionAttemptLifecycle lifecycle;

    @Inject
    ExecutionEventPublisher events;

    @Inject
    ExecutionFederationMapper federationMapper;

    @Inject
    TestEmbedConnections connections;

    @Inject
    @RestClient
    GlobalRelayService globalRelayService;

    @Inject
    @RestClient
    LocalControllerLearningService controllerService;

    public static boolean isHosted(ExecutionAttemptEntity attempt) {
        return isHosted(attempt.getStep().getRun());
    }

    public static boolean isHosted(ExecutionRunEntity run) {
        return run.getPurpose() == ExecutionPurpose.TEST && run.getEngineMode() == TestEngineMode.FEDERATED_RUN;
    }

    public ExecutionRunEntity start(FederatedAppVersionEntity version, FederatedTestRunCreateDTO create, String author) {
        if (create == null || !version.getFederatedApp().isSupportsFederatedLearning()) {
            throw new BadRequestException("A federated test needs a federated tool and participant configuration");
        }
        create.setFederatedAppVersionId(version.getId());
        Long runId = testRunBO.startFederatedTestTransactional(version.getFederatedApp().getId(), create, author);
        ExecutionRunEntity run = runAO.findById(runId);
        run.setEngineMode(TestEngineMode.FEDERATED_RUN);
        run.setLocation(ExecutionLocation.PLATFORM);
        return run;
    }

    public void prepare(ExecutionRunStepEntity step) {
        ExecutionFederationEntity federation = step.getFederation();
        if (!federation.getRelayClientIds().isEmpty()) {
            return;
        }
        List<ExecutionAttemptEntity> clients = step.getAttempts().stream()
                .filter(attempt -> roleOf(attempt) == FederatedParticipantType.CLIENT)
                .toList();
        CreateFLLearningRelayServerResponseDTO created = globalRelayService.setupFL(
                CreateFLLearningRelayServerRequestDTO.createForV2(clients.size()));
        // Channel first, so failure cleanup can still stop it.
        federation.getConfig().setChannel(created.getChannel());
        List<String> clientIds = List.copyOf(created.getClientIds());
        if (clientIds.size() != clients.size()) {
            try (Response ignored = globalRelayService.stopFL(new RelayStopRequestDTO(created.getChannel()))) {
                throw new IllegalStateException("Relay returned the wrong number of client slots");
            }
        }
        for (ExecutionAttemptEntity attempt : step.getAttempts()) {
            boolean coordinator = roleOf(attempt) == FederatedParticipantType.AGGREGATOR;
            String clientId = coordinator ? created.getCoordinatorId() : clientIds.get(clients.indexOf(attempt));
            FederatedRunConfigDTO slot = created.runConfigOf(RelayServerAppVersions.v2);
            slot.setClientId(clientId);
            slot.setClientKey(coordinator ? created.getCoordinatorKey() : created.getClientId2ClientKey().get(clientId));
            attempt.getFederation().setRelayConfig(slot);
            federation.getRelayClientIds().put(participantOf(attempt).getParticipantId(), clientId);
            if (federation.getTotalRounds() != null) {
                attempt.getParams().putIfAbsent("federated_rounds", federation.getTotalRounds());
            }
        }
    }

    public void customizeStartup(ExecutionAttemptEntity attempt, StartRunDTO start) {
        ExecutionParticipantEntity participant = participantOf(attempt);
        ExecutionRunStepEntity step = attempt.getStep();
        ExecutionFederationEntity federation = step.getFederation();
        boolean noInput = participant.getInputFilePaths() == null || participant.getInputFilePaths().isEmpty();

        start.setParticipants(List.of(federationMapper.attemptToParticipantDTO(attempt)));
        start.setInputFilePaths(participant.getInputFilePaths());
        start.setStartAggregator(participant.getRole() == FederatedParticipantType.AGGREGATOR);
        start.setAggregatorOnly(start.getStartAggregator() && !participant.isCoordinator() && noInput);
        start.setTotalRounds(federation.getTotalRounds());
        start.setDistributedTest(true);
        start.setSupportFederatedLearning(true);

        FederatedRunConfigDTO config = new FederatedRunConfigDTO();
        config.setChannel(federation.getConfig().getChannel());
        config.setCoordinatorId(participantOf(step.getSelectedAttempt()).getParticipantId());
        config.setOrderClientIds(step.getAttempts().stream()
                .map(HostedFederatedTestBO::participantOf)
                .filter(other -> other.getRole() == FederatedParticipantType.CLIENT)
                .map(ExecutionParticipantEntity::getParticipantId)
                .toList());
        config.setPollInterval(federation.getConfig().getPollInterval());
        config.setTimeout(federation.getConfig().getTimeout());
        config.setMaxPolls(federation.getConfig().getMaxPolls());
        start.setConfig(config);
    }

    @Transactional
    public void updateParticipantTransactional(Long attemptId, FederatedParticipantUpdateDTO update) {
        ExecutionAttemptEntity attempt = hostedAttemptOf(attemptId);
        attemptAO.applyParticipantUpdate(attempt, update);
        events.attemptUpdated(attemptId);
    }

    @Transactional
    public void storeRoundTransactional(Long attemptId, FederatedRoundMessageCreateDTO create) {
        ExecutionAttemptEntity attempt = hostedAttemptOf(attemptId);
        // Relay ids back to participant ids
        Map<String, String> relayClientIds = attempt.getStep().getFederation().getRelayClientIds();
        relayClientIds.forEach((participantId, clientId) -> {
            if (clientId.equals(create.getFromParticipant())) {
                create.setFromParticipant(participantId);
            }
            if (clientId.equals(create.getToParticipant())) {
                create.setToParticipant(participantId);
            }
        });
        ExecutionMessageRoundDTO round = federationMapper.createDtoToRoundDTO(create);
        lifecycle.storeRoundTransactional(attemptId, round);
        send(attempt, AppMessageTypeEnum.FEDERATED_ROUND_MESSAGE, round, AppRunTypeEnum.FEDERATED_TEST_RUN);
    }

    @Transactional
    public void publishTransactional(Long attemptId, AppMessageTypeEnum type, Object payload) {
        Optional<ExecutionAttemptEntity> found = attemptAO.findByIdOptional(attemptId).filter(TestEngineRunBO::isRunContainer);
        if (found.isEmpty()) {
            return;
        }
        ExecutionAttemptEntity attempt = found.get();
        send(attempt, type, payload, isHosted(attempt) ? AppRunTypeEnum.FEDERATED_TEST_RUN : AppRunTypeEnum.TEST_RUN);
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void settleStepTransactional(Long stepId) {
        Optional<ExecutionRunStepEntity> found = stepAO.findByIdWrite(stepId);
        if (found.isEmpty() || found.get().hasEnded() || !isHosted(found.get().getRun())) {
            return;
        }
        ExecutionRunStepEntity step = found.get();
        Optional<ExecutionAttemptEntity> failed = step.getAttempts().stream()
                .filter(attempt -> attempt.hasEnded() && attempt.getStatus() != RunStatusTypes.FINISHED)
                .findFirst();
        if (failed.isPresent()) {
            ExecutionAttemptEntity attempt = failed.get();
            String error = attempt.getFailure() != null ? attempt.getFailure().getMessage() : "A participant stopped";
            cleanupBO.endFederatedStep(step, attempt.getStatus(), error);
            return;
        }
        if (step.getAttempts().stream().allMatch(ExecutionAttemptEntity::hasEnded)) {
            step.getFederation().setCurrentRound(step.getAttempts().stream()
                    .mapToInt(attempt -> attempt.getFederation().getCurrentRound())
                    .min()
                    .orElse(0));
            cleanupBO.endFederatedStep(step, RunStatusTypes.FINISHED, null);
        }
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void cleanupRunTransactional(Long runId) {
        Optional<ExecutionRunEntity> found = runAO.findByIdWrite(runId);
        if (found.isEmpty() || !isHosted(found.get())) {
            return;
        }
        ExecutionRunEntity run = found.get();
        ExecutionFederationEntity federation = run.getSteps().getFirst().getFederation();
        String channel = federation.getConfig().getChannel();
        if (Boolean.TRUE.equals(federation.getRelayStopped()) || channel == null) {
            return;
        }
        boolean stopped = true;
        for (ExecutionParticipantEntity participant : run.getParticipants()) {
            stopped &= stopController(channel, participant.getParticipantId());
        }
        stopRelay(channel);
        federation.setRelayStopped(stopped);
        Log.infof("Stopped relay channel of hosted federated test %d", runId);
    }

    private boolean stopController(String channel, String participantId) {
        try (Response response = controllerService.stopLearning(new ControllerStopLearningRequestDTO(channel, participantId))) {
            if (isStoppedOrGone(response)) {
                return true;
            }
            Log.warnf("Controller answered %d when stopping participant %s", response.getStatus(), participantId);
            return false;
        } catch (NotFoundException e) {
            e.getResponse().close();
            return true;
        } catch (RuntimeException e) {
            Log.warnf(e, "Could not stop controller participant %s", participantId);
            return false;
        }
    }

    private void stopRelay(String channel) {
        try (Response response = globalRelayService.stopFL(new RelayStopRequestDTO(channel))) {
            if (!isStoppedOrGone(response)) {
                throw new IllegalStateException("Could not stop relay channel " + channel);
            }
        } catch (WebApplicationException e) {
            if (e.getResponse().getStatus() != Response.Status.NOT_FOUND.getStatusCode()) {
                throw e;
            }
            e.getResponse().close();
        }
    }

    private static boolean isStoppedOrGone(Response response) {
        return response.getStatus() == Response.Status.OK.getStatusCode()
                || response.getStatus() == Response.Status.NOT_FOUND.getStatusCode();
    }

    private ExecutionAttemptEntity hostedAttemptOf(Long attemptId) {
        ExecutionAttemptEntity attempt = attemptAO.findByIdWrite(attemptId).orElseThrow(NotFoundException::new);
        if (!isHosted(attempt)) {
            throw new BadRequestException(NOT_HOSTED);
        }
        return attempt;
    }

    private void send(ExecutionAttemptEntity attempt, AppMessageTypeEnum type, Object payload, AppRunTypeEnum runType) {
        Long appId = attempt.getStep().getFederatedAppVersion().getFederatedApp().getId();
        connections.send(appId, AppConnectionTypesEnum.CLIENT, new AppMessageWrapperDTO<>(type, payload, runType));
    }

    private static ExecutionParticipantEntity participantOf(ExecutionAttemptEntity attempt) {
        return attempt.getFederation().getParticipant();
    }

    private static FederatedParticipantType roleOf(ExecutionAttemptEntity attempt) {
        return participantOf(attempt).getRole();
    }
}

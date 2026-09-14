package de.unihamburg.daibetes.api.project.experiment.federated;

import bio.cosy.feddb.core.api.project.ProjectStatus;
import de.unihamburg.daibetes.api.project.experiment.federated.participants.ProjectFederatedExperimentParticipantEntity;
import io.quarkus.logging.Log;

import java.security.SecureRandom;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class ProjectFederatedExperimentHelper {

    public static String createRandomChannelID() {
        // Create a random channel ID for the experiment
        // global-learning-api needs to generate it and send the same to all participants (
        // random 32 bytes, hex encoded to string, e.g. "Ab..." -> "4162...")
        byte[] randomBytes = new byte[32];
        new SecureRandom().nextBytes(randomBytes);
        StringBuilder sb = new StringBuilder(randomBytes.length * 2);
        for (byte b : randomBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static ProjectStatus getNextStatus(Set<ProjectFederatedExperimentParticipantEntity> participants) {
        if (participants == null || participants.isEmpty()) {
            Log.errorf("No participant found for this federated experiment");
            return ProjectStatus.ERROR;
        }
        Map<ProjectStatus, Long> statusCounts = participants.stream().collect(
                Collectors.groupingBy(
                        p -> {
                            ProjectStatus s = p.getStepStatus();
                            if (s == null) {
                                Log.warn("Participant has null stepStatus; treating as INIT");
                                return ProjectStatus.INIT;
                            }
                            return s;
                        },
                        () -> new EnumMap<>(ProjectStatus.class),
                        Collectors.counting()
                )
        );


        int total = participants.size();
        int readyCount = statusCounts.getOrDefault(ProjectStatus.READY, 0L).intValue();
        int runningCount = statusCounts.getOrDefault(ProjectStatus.RUNNING, 0L).intValue();
        int prepareCount = statusCounts.getOrDefault(ProjectStatus.PREPARE, 0L).intValue();
        int finishedCount = statusCounts.getOrDefault(ProjectStatus.FINISHED, 0L).intValue();
        int stoppedCount = statusCounts.getOrDefault(ProjectStatus.STOPPED, 0L).intValue();
        int shutdownCount = statusCounts.getOrDefault(ProjectStatus.SHUTDOWN, 0L).intValue();
        int errorCount = statusCounts.getOrDefault(ProjectStatus.ERROR, 0L).intValue();

        boolean allReady = readyCount == total;
        boolean allRunning = runningCount == total;
        boolean anyError = errorCount > 0;
        boolean anyStopped = stoppedCount > 0;
        boolean anyShutdown = shutdownCount > 0;
        boolean allFinished = finishedCount == total;

        ProjectStatus newStatus;
        if (allFinished) {
            newStatus = ProjectStatus.FINISHED;
        } else if (anyError) {
            newStatus = ProjectStatus.ERROR;
        } else if (anyShutdown) {
            newStatus = ProjectStatus.SHUTDOWN;
        } else if (anyStopped) {
            newStatus = ProjectStatus.STOPPED;
        } else if (allRunning) {
            newStatus = ProjectStatus.RUNNING;
        } else if (prepareCount > 0) {
            newStatus = ProjectStatus.PREPARE;
        } else if (allReady) {
            newStatus = ProjectStatus.READY;
        } else {
            // Fallback if mixed/initial states without a stronger signal
            newStatus = ProjectStatus.INIT;
        }
        return newStatus;
    }
}

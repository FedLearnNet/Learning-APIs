package bio.cosy.feddb.core.api.maintenance;

import bio.cosy.feddb.core.api.info.ReleaseVersion;
import bio.cosy.feddb.core.services.orch.clients.MaintenanceServiceClient;
import bio.cosy.feddb.core.services.orch.dto.BackupDTO;
import bio.cosy.feddb.core.services.orch.dto.MaintenanceMode;
import bio.cosy.feddb.core.services.orch.dto.MaintenanceStatusDTO;
import bio.cosy.feddb.core.services.orch.dto.UpgradeStateDTO;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;


public abstract class BaseMaintenanceBO {

    private static final Duration STATUS_TIMEOUT = Duration.ofSeconds(30);

    @ConfigProperty(name = "quarkus.application.version")
    String version;

    @Inject
    @RestClient
    MaintenanceServiceClient orch;

    protected abstract String targetVersion(String requested);

    protected abstract long countOpenRunsTransactional();

    public Multi<MaintenanceDTO> loadMaintenance() {
        return runOnWorker(() -> addVersions(orch.status()));
    }

    public Multi<MaintenanceDTO> start(MaintenanceMode mode, String requested, boolean all, String backup) {
        return runOnWorker(() -> {
            MaintenanceStatusDTO status = loadStatus();
            checkIfRunning(status);
            String target = findTargetVersion(mode, requested, status);
            if (mode == MaintenanceMode.RESTORE) {
                checkIfRestorable(status, backup);
            }
            checkOpenRuns(mode);
            return addVersions(orch.start(mode, target, all, backup));
        });
    }

    private MaintenanceStatusDTO loadStatus() {
        MaintenanceStatusDTO status = orch.status().toUni().await().atMost(STATUS_TIMEOUT);
        if (status.getLastError() != null) {
            throw conflict(status.getLastError());
        }
        return status;
    }

    private static void checkIfRunning(MaintenanceStatusDTO status) {
        if (status.getRunning() != null) {
            throw conflict("A backup or upgrade is already running.");
        }
    }

    private String findTargetVersion(MaintenanceMode mode, String requested, MaintenanceStatusDTO status) {
        String target = switch (mode) {
            // a rollback runs the flnet of the version it goes back from
            case ROLLBACK ->
                    Optional.ofNullable(status.getLastUpgrade()).map(UpgradeStateDTO::getFlNetSoftwareVersion).orElse(null);
            case BACKUP, RESTORE -> version;
            default -> targetVersion(requested);
        };
        if (target == null || ReleaseVersion.parse(target).isEmpty()) {
            throw conflict("There is no version to " + mode.name().toLowerCase(Locale.ROOT) + ".");
        }
        return target;
    }

    private void checkIfRestorable(MaintenanceStatusDTO status, String name) {
        BackupDTO backup = status.getBackups().stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst()
                .orElseThrow(() -> conflict("There is no backup " + name + "."));
        if (!backup.isComplete()) {
            throw conflict("Backup " + name + " is not complete.");
        }
        if (!version.equals(backup.getVersion())) {
            throw conflict("Backup " + name + " is of " + backup.getVersion() + ", this runs " + version
                    + ". Restore it on the server with flnet.");
        }
    }

    private void checkOpenRuns(MaintenanceMode mode) {
        if (mode == MaintenanceMode.PREPARE || mode == MaintenanceMode.BACKUP) {
            return;
        }
        long open = countOpenRunsTransactional();
        if (open > 0) {
            throw conflict(open + " executions are still open. Wait for them to finish or stop them first.");
        }
    }

    private Multi<MaintenanceDTO> runOnWorker(Supplier<Multi<MaintenanceDTO>> stream) {
        return Uni.createFrom().item(stream)
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .onItem().transformToMulti(Function.identity())
                .onFailure().recoverWithItem(this::buildErrorStatus);
    }

    private Multi<MaintenanceDTO> addVersions(Multi<MaintenanceStatusDTO> states) {
        String available = targetVersion(null);
        return states.map(state -> new MaintenanceDTO(version, available, state));
    }

    private MaintenanceDTO buildErrorStatus(Throwable failure) {
        MaintenanceStatusDTO status = new MaintenanceStatusDTO();
        status.setLastError(failure.getMessage());
        return new MaintenanceDTO(version, null, status);
    }

    private static ClientErrorException conflict(String message) {
        return new ClientErrorException(message, Response.Status.CONFLICT);
    }
}

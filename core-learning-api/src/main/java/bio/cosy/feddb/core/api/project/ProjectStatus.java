package bio.cosy.feddb.core.api.project;

import bio.cosy.feddb.core.api.run.RunStatusTypes;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public enum ProjectStatus {
    INIT("Init"),
    READY("Ready"),  // People can join
    PREPARE("Prepare"),  // Create input volumes and prepare everything for run. No one can join anymore
    RUNNING("Running"),  // Workflow is running
    SHUTDOWN("Shutdown"),  // Workflow is stopping
    STOPPED("Stopped"),  // Workflow has been stopped, containers are shut down
    ERROR("Error"),  // Workflow has been stopped, containers are shut down
    FINISHED("Finished");  // Workflow is finished

    private final String description;

    ProjectStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description.toLowerCase();
    }

    public static List<String> getStatusList() {
        return Arrays.stream(ProjectStatus.values())
                .map(ProjectStatus::name)
                .collect(Collectors.toList());
    }

    public static boolean contains(ProjectStatus status) {
        return Arrays.asList(ProjectStatus.values()).contains(status);
    }

    public static boolean contains(String status) {
        return Arrays.stream(ProjectStatus.values())
                .anyMatch(e -> e.equalsString(status));
    }

    @Override
    public String toString() {
        return this.getDescription();
    }

    public boolean equalsString(String name) {
        return this.toString().equalsIgnoreCase(name);
    }

    public static ProjectStatus valueOfIgnoreCase(String name) {
        return Arrays.stream(ProjectStatus.values())
                .filter(e -> e.name().equalsIgnoreCase(name))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No enum constant " + ProjectStatus.class.getCanonicalName() + "." + name));
    }

    public static boolean isFinalStatus(ProjectStatus status) {
        if (status == null) {
            return false;
        }
        return status == FINISHED || status == STOPPED || status == ERROR || status == SHUTDOWN;
    }

    public static boolean isPreRunning(ProjectStatus status) {
        if (status == null) {
            return false;
        }
        return status == INIT || status == READY || status == PREPARE;
    }

    public static ProjectStatus fromRunStatusTypes(RunStatusTypes runStatus) {
        return switch (runStatus) {
            case PENDING, INITIALIZED, STARTED -> READY;
            case RUNNING -> RUNNING;
            case FINISHED -> FINISHED;
            case STOPPED -> STOPPED;
            case ERROR -> ERROR;
        };
    }

}

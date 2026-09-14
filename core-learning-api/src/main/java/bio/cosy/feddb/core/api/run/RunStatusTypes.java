package bio.cosy.feddb.core.api.run;

import bio.cosy.feddb.core.api.project.ProjectStatus;

import java.util.List;

public enum RunStatusTypes {

    PENDING("pending"),
    INITIALIZED("initialized"),
    STARTED("started"),
    RUNNING("running"),
    FINISHED("finished"),
    STOPPED("stopped"),
    ERROR("error");

    private final String name;

    RunStatusTypes(String s) {
        name = s;
    }

    public String toString() {
        return this.name.toUpperCase();
    }

    public ProjectStatus getProjectStatus() {
        return switch (this) {
            case PENDING, INITIALIZED -> ProjectStatus.INIT;
            case STARTED, RUNNING -> ProjectStatus.RUNNING;
            case FINISHED -> ProjectStatus.FINISHED;
            case ERROR -> ProjectStatus.ERROR;
            default -> throw new IllegalArgumentException("Unknown RunStatusType: " + this);
        };
    }

    public static boolean isFinalStatus(RunStatusTypes status) {
        if (status == null) {
            return false;
        }
        return terminalStates().contains(status);
    }

    public static boolean canBeStartedStatus(RunStatusTypes status) {
        if (status == null) {
            return false;
        }
        return startableStates().contains(status);
    }


    public static List<RunStatusTypes> terminalStates() {
        return List.of(
                RunStatusTypes.FINISHED,
                RunStatusTypes.STOPPED,
                RunStatusTypes.ERROR
        );
    }

    public static List<RunStatusTypes> startableStates() {
        return List.of(
                RunStatusTypes.PENDING,
                RunStatusTypes.INITIALIZED,
                RunStatusTypes.STARTED
        );
    }
}

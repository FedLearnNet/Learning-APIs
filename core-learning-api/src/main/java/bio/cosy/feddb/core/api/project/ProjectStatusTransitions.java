package bio.cosy.feddb.core.api.project;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static bio.cosy.feddb.core.api.project.ProjectStatus.*;

public class ProjectStatusTransitions {
    private static final Map<ProjectStatus, List<ProjectStatus>> transitions = new HashMap<>();

    static {
        transitions.put(INIT, List.of(READY));
        transitions.put(READY, List.of(PREPARE));
        transitions.put(PREPARE, Arrays.asList(READY, RUNNING));
        transitions.put(RUNNING, Arrays.asList(SHUTDOWN, FINISHED));
        transitions.put(SHUTDOWN, List.of(STOPPED));
        transitions.put(STOPPED, List.of(READY));
        transitions.put(ERROR, List.of(READY));
        transitions.put(FINISHED, List.of(READY));
    }

    public static boolean isValidTransition(ProjectStatus from, ProjectStatus to) {
        return transitions.get(from).contains(to);
    }

    public static boolean isValidTransition(String from, String to) {
        return isValidTransition(ProjectStatus.valueOfIgnoreCase(from), ProjectStatus.valueOfIgnoreCase(to));
    }
}

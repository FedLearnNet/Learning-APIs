package de.unihamburg.daibetes.api.app.startup;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

@Data
public class ToolStartupGeneratorCreateDTO {
    private Boolean enableConfigSync;
    private Boolean tracePerformance;
    private Boolean enableProjectSetup;
    private Boolean prioLocalConfig;
    private Boolean sendConsoleLogs;

    @JsonIgnore
    public boolean isConfigSyncEnabled() {
        return enableConfigSync != null && enableConfigSync;
    }

    @JsonIgnore
    public boolean isProjectSetupEnabled() {
        return enableProjectSetup != null && enableProjectSetup;
    }

    @JsonIgnore
    public boolean isPrioLocalConfig() {
        return prioLocalConfig != null && prioLocalConfig;
    }

    @JsonIgnore
    public boolean isTracePerformance() {
        return tracePerformance != null && tracePerformance;
    }

    @JsonIgnore
    public boolean isSendConsoleLogs() {
        return sendConsoleLogs != null && sendConsoleLogs;
    }

}

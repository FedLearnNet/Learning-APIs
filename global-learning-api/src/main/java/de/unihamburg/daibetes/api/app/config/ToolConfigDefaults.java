package de.unihamburg.daibetes.api.app.config;

import bio.cosy.feddb.core.api.app.FederatedAppType;
import bio.cosy.feddb.core.api.app.config.*;

import java.util.EnumMap;
import java.util.Map;

public final class ToolConfigDefaults {

    private static final ToolConfigsDTO EMPTY = new ToolConfigsDTO();

    private static final Map<FederatedAppType, ToolConfigsDTO> DEFAULTS;

    static {
        EnumMap<FederatedAppType, ToolConfigsDTO> m = new EnumMap<>(FederatedAppType.class);

        m.put(FederatedAppType.EXPORT, exporterDefaults());
        m.put(FederatedAppType.DATA_TRANSFORMATION, transformerDefaults());
        m.put(FederatedAppType.PRE_PROCESSING, inputOnlyDefaults());

        DEFAULTS = Map.copyOf(m);
    }

    public static ToolConfigsDTO forType(FederatedAppType type) {
        if (type == null) {
            return EMPTY;
        }
        return DEFAULTS.getOrDefault(type, EMPTY);
    }

    private static ToolConfigsDTO exporterDefaults() {
        ToolConfigsDTO cfg = new ToolConfigsDTO();
        cfg.getInput().add(staticCsvInput("input"));
        return cfg;
    }

    private static ToolConfigsDTO transformerDefaults() {
        ToolConfigsDTO cfg = new ToolConfigsDTO();
        cfg.getInput().add(staticCsvInput("input"));
        cfg.getOutput().add(staticCsvOutput("output"));
        return cfg;
    }

    private static ToolConfigsDTO inputOnlyDefaults() {
        ToolConfigsDTO cfg = new ToolConfigsDTO();
        cfg.getOutput().add(staticCsvOutput("output"));
        return cfg;
    }

    private static ToolInputConfigDTO staticCsvInput(String name) {
        ToolInputConfigDTO dto = new ToolInputConfigDTO();
        dto.setName(name);
        dto.setVariableName(name);
        dto.setType(ToolConfigDataType.CSV);
        dto.setMode(ToolConfigModeType.BOTH);
        dto.setDelimiter(",");
        dto.setHasHeader(true);
        dto.setRequired(true);
        dto.setDescription("Static CSV input");
        return dto;
    }

    private static ToolOutputConfigDTO staticCsvOutput(String name) {
        ToolOutputConfigDTO dto = new ToolOutputConfigDTO();
        dto.setName(name);
        dto.setVariableName(name);
        dto.setType(ToolConfigDataType.CSV);
        dto.setMode(ToolConfigModeType.BOTH);
        dto.setDelimiter(",");
        dto.setHasHeader(true);
        dto.setDescription("Static CSV output");
        return dto;
    }
}

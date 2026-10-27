package bio.cosy.feddb.core.execution.executor;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.LinkedHashMap;

@Data
@NoArgsConstructor
public class ExecutionStartDTO {

    private String name;

    @NotNull
    private ExecutionPurpose purpose;

    @NotNull
    private ExecutionMode mode = ExecutionMode.SINGLE;

    // Search per workflow node id, chosen in the frontend; a node without one runs once with its own hyper parameters
    @NotNull
    private LinkedHashMap<Long, @Valid @NotNull HyperParamSearchDTO> searches = new LinkedHashMap<>();

    // Input data per workflow node id, e.g. values or references to input files
    @NotNull
    private LinkedHashMap<Long, @NotNull LinkedHashMap<String, Object>> inputs = new LinkedHashMap<>();

    public ExecutionStartDTO(ExecutionPurpose purpose, ExecutionMode mode, String name) {
        this.purpose = purpose;
        this.mode = mode;
        this.name = name;
    }
}

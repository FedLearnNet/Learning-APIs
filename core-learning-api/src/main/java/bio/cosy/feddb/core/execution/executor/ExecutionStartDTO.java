package bio.cosy.feddb.core.execution.executor;

import bio.cosy.feddb.core.execution.ExecutionMode;
import bio.cosy.feddb.core.execution.ExecutionPurpose;
import bio.cosy.feddb.core.execution.hyperparam.config.HyperParamSearchDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.LinkedHashMap;

@Data
public class ExecutionStartDTO {

    private String name;

    @NotNull
    private ExecutionPurpose purpose;

    @NotNull
    private ExecutionMode mode = ExecutionMode.SINGLE;

    // Search per workflow node id, chosen in the frontend; a node without one runs once with its own hyper parameters
    @NotNull
    private LinkedHashMap<Long, @Valid @NotNull HyperParamSearchDTO> searches = new LinkedHashMap<>();
}

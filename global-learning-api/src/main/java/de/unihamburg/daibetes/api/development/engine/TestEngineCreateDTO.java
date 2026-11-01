package de.unihamburg.daibetes.api.development.engine;

import de.unihamburg.daibetes.api.development.run.TestRunCreateDTO;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class TestEngineCreateDTO {
    @NotNull
    private TestEngineMode mode;
    // The run a SINGLE_RUN engine serves
    private TestRunCreateDTO testRun;
}

package eval.agent.etl.transform.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** What one schema node cost the assistant, over the first pass and the review pass. */
@Data
@NoArgsConstructor
public class TransformerWriterEffort {
    private String node;
    private long durationMs;
    private int modelCalls;
    private int questions;
    // Chains and code checked by the validator, the last one valid if the node is found
    private int validations;
    // Where the result comes from: direct, built-in, store tool, saved code, new code or not found
    private String source;
    private List<String> steps = new ArrayList<>();

    public TransformerWriterEffort(String node) {
        this.node = node;
    }
}

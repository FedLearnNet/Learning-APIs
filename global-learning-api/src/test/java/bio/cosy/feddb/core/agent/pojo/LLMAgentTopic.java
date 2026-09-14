package bio.cosy.feddb.core.agent.pojo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class LLMAgentTopic {
    private String topic;
    private List<LLMAgentQuestion> questions = new ArrayList<>();
}

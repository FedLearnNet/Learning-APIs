package bio.cosy.feddb.local.agent.etl.transform.guardrails;

import jakarta.enterprise.context.RequestScoped;

import java.util.HashSet;
import java.util.Set;

@RequestScoped
public class AnswerHints {

    private final Set<String> given = new HashSet<>();

    public void reset() {
        given.clear();
    }

    public boolean once(String hint) {
        return given.add(hint);
    }
}

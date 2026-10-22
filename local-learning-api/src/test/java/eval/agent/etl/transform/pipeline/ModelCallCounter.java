package eval.agent.etl.transform.pipeline;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.concurrent.atomic.AtomicInteger;

/** Counts the requests to every chat model; the assistant handles one schema node at a time. */
@ApplicationScoped
public class ModelCallCounter implements ChatModelListener {

    private final AtomicInteger calls = new AtomicInteger();

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        calls.incrementAndGet();
    }

    public int calls() {
        return calls.get();
    }
}

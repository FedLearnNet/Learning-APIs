package bio.cosy.feddb.local.agent.etl.transform.memory;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.function.Supplier;

public class ColumnSelectMemory implements Supplier<ChatMemoryProvider> {

    private static final int MAX_MESSAGES = 30;

    @Override
    public ChatMemoryProvider get() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(MAX_MESSAGES)
                .build();
    }
}

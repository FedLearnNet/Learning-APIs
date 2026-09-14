package de.unihamburg.daibetes.agent.anlysis.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.util.function.Supplier;

public class ChatMemoryProviderSupplier implements Supplier<ChatMemoryProvider> {
    @Override
    public ChatMemoryProvider get() {
        return new ChatMemoryProvider() {
            @Override
            public ChatMemory get(Object memoryId) {
                return new MessageWindowChatMemory.Builder().maxMessages(5).build();
            }
        };
    }
}

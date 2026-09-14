package de.unihamburg.daibetes.agent.anlysis.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class MemoryStore implements ChatMemoryStore {
    private static final TypeReference<List<ChatMessage>> MESSAGE_LIST_TYPE = new TypeReference<>() {
    };

    Map<String, List<ChatMessage>> messages = new HashMap<>();

    @Override
    public void deleteMessages(Object memoryId) {
        if (memoryId instanceof Long) {
            messages.remove(memoryId.toString());
        } else if (memoryId instanceof String) {
            messages.remove(memoryId);
        } else {
            throw new IllegalArgumentException("Memory ID must be a String");
        }
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        if (memoryId instanceof Long) {
            return messages.getOrDefault(memoryId.toString(), Collections.emptyList());
        } else if (memoryId instanceof String) {
            return messages.getOrDefault(memoryId, Collections.emptyList());
        } else {
            throw new IllegalArgumentException("Memory ID must be a String");
        }

    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (memoryId instanceof Long) {
            this.messages.put(memoryId.toString(), messages);
        } else if (memoryId instanceof String) {
            this.messages.put((String) memoryId, messages);
        } else {
            throw new IllegalArgumentException("Memory ID must be a String");
        }

    }
}


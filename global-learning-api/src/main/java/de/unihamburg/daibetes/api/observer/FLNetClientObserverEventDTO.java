package de.unihamburg.daibetes.api.observer;

import lombok.Data;

import java.util.Date;

@Data
public class FLNetClientObserverEventDTO {

    private String connectionId;
    private FLNetClientObserverEvenType type;
    private String messageType;
    private Object payload;
    private Date timestamp;

    private FLNetClientObserverEventDTO(String connectionId, FLNetClientObserverEvenType type, String messageType, Object payload) {
        this.connectionId = connectionId;
        this.type = type;
        this.messageType = messageType;
        this.payload = payload;
        this.timestamp = new Date();
    }

    public static FLNetClientObserverEventDTO connected(String connectionId) {
        return new FLNetClientObserverEventDTO(connectionId, FLNetClientObserverEvenType.CONNECTED, null, null);
    }

    public static FLNetClientObserverEventDTO disconnected(String connectionId) {
        return new FLNetClientObserverEventDTO(connectionId, FLNetClientObserverEvenType.DISCONNECTED, null, null);
    }

    public static FLNetClientObserverEventDTO received(String connectionId, String messageType, Object payload) {
        return new FLNetClientObserverEventDTO(connectionId, FLNetClientObserverEvenType.MESSAGE_RECEIVED, messageType, payload);
    }

    public static FLNetClientObserverEventDTO sent(String connectionId, String messageType, Object payload) {
        return new FLNetClientObserverEventDTO(connectionId, FLNetClientObserverEvenType.MESSAGE_SENT, messageType, payload);
    }
}

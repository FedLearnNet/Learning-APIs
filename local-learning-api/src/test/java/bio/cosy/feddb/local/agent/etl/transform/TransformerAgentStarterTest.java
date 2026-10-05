package bio.cosy.feddb.local.agent.etl.transform;

import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.organizer.TransformerLoop;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransformerAgentStarterTest {

    private final TransformerAgentStarter starter = new TransformerAgentStarter();
    private final List<TransformerAssistantMessageDTO> sent = new CopyOnWriteArrayList<>();
    private final List<String> answers = new CopyOnWriteArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        starter.executor = Executors.newVirtualThreadPerTaskExecutor();
        starter.transformerLoop = mock(TransformerLoop.class);
        doAnswer(invocation -> {
            Consumer<TransformerAssistantMessageDTO> events = invocation.getArgument(1);
            UnaryOperator<String> askUser = invocation.getArgument(3);
            answers.add(askUser.apply("Which column holds the age?"));
            events.accept(TransformerAssistantMessageDTO.node(TransformerAssistantMessageType.NODE_RESULT, 1L,
                    "Patient.age", ConnectorSelectedTransformer.mapping(answers.getFirst(), List.of())));
            return null;
        }).when(starter.transformerLoop).run(any(ConnectorDTO.class), any(Consumer.class), any(Supplier.class), any(UnaryOperator.class));
        when(starter.transformerLoop.toConnector(anyCollection())).thenAnswer(invocation -> {
            ConnectorDTO connector = new ConnectorDTO();
            connector.setDescription(String.valueOf(((java.util.Collection<?>) invocation.getArgument(0)).size()));
            return connector;
        });
    }

    @Test
    void asksTheUserAndContinuesWithTheAnswer() {
        starter.start("session", new ConnectorDTO(), sent::add);

        await().atMost(Duration.ofSeconds(5)).until(() -> sent.stream().anyMatch(this::isOpenQuestion));
        starter.message("session", "age_years");
        await().atMost(Duration.ofSeconds(5)).until(() -> hasType(TransformerAssistantMessageType.DONE));

        assertEquals(List.of("age_years"), answers);
        assertTrue(hasType(TransformerAssistantMessageType.NODE_RESULT));
        assertEquals("1", starter.apply("session", List.of(1L, 2L)).getDescription());
    }

    @Test
    void stopsARunThatWaitsForAnAnswer() {
        starter.start("session", new ConnectorDTO(), sent::add);
        await().atMost(Duration.ofSeconds(5)).until(() -> sent.stream().anyMatch(this::isOpenQuestion));

        starter.stop("session");

        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).until(() -> !hasType(TransformerAssistantMessageType.DONE));
        assertTrue(answers.isEmpty());
    }

    private boolean isOpenQuestion(TransformerAssistantMessageDTO message) {
        return message.getType() == TransformerAssistantMessageType.CHAT_MESSAGE && !message.getChatMessage().isDone();
    }

    private boolean hasType(TransformerAssistantMessageType type) {
        return sent.stream().anyMatch(message -> message.getType() == type);
    }
}

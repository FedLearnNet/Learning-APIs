package bio.cosy.feddb.local.agent.etl.transform;

import bio.cosy.feddb.core.api.model.workflow.chat.HumanInTheLoopType;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageType;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantUser;
import bio.cosy.feddb.local.agent.etl.transform.helper.TransformerCombiner;
import bio.cosy.feddb.local.agent.etl.transform.model.ConnectorSelectedTransformer;
import bio.cosy.feddb.local.agent.etl.transform.organizer.TransformerLoop;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.mapping.ConnectorMappingDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TransformerAgentStarterTest {

    private final TransformerAgentStarter starter = new TransformerAgentStarter();
    private final List<TransformerAssistantMessageDTO> sent = new CopyOnWriteArrayList<>();
    private final List<String> answers = new CopyOnWriteArrayList<>();

    @BeforeEach
    void setUp() {
        starter.executor = Executors.newVirtualThreadPerTaskExecutor();
        starter.transformerLoop = mock(TransformerLoop.class);
        starter.combiner = mock(TransformerCombiner.class);
        when(starter.combiner.combine(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void asksTheUserAndContinuesWithTheAnswer() {
        loop((events, user) -> {
            answers.add(user.ask("Which column holds the age?"));
            events.accept(TransformerAssistantMessageDTO.node(TransformerAssistantMessageType.NODE_RESULT, 1L,
                    "Patient.age", ConnectorSelectedTransformer.mapping(answers.getFirst(), List.of())));
        });
        starter.start("session", new ConnectorDTO(), "keycloak-user", sent::add);

        await().atMost(Duration.ofSeconds(5)).until(() -> sent.stream().anyMatch(this::isOpenQuestion));
        starter.message("session", userMessage("age_years", null));
        await().atMost(Duration.ofSeconds(5)).until(() -> hasType(TransformerAssistantMessageType.DONE));

        assertEquals(List.of("age_years"), answers);
        assertTrue(hasType(TransformerAssistantMessageType.NODE_RESULT));
        List<ConnectorMappingDTO> mappings = starter.apply("session", List.of(1L, 2L)).getSchemaMapping();
        assertEquals(1, mappings.size());
        assertEquals("age_years", mappings.getFirst().getColumn());
        assertEquals("Patient.age", mappings.getFirst().getMapping());
    }

    @Test
    void appliesThePatientIdColumnTheUserSelected() {
        loop((events, user) -> {
            List<String> selected = user.selectColumns("Which column holds the patient ID?",
                    List.of("patient_nbr", "encounter_id"), false);
            answers.addAll(selected);
            events.accept(TransformerAssistantMessageDTO.patientId("Unique Patient ID", selected.getFirst()));
            events.accept(TransformerAssistantMessageDTO.node(TransformerAssistantMessageType.NODE_RESULT, 1L,
                    "Patient.age", ConnectorSelectedTransformer.mapping("age", List.of())));
        });
        starter.start("session", new ConnectorDTO(), "keycloak-user", sent::add);

        await().atMost(Duration.ofSeconds(5)).until(() -> sent.stream().anyMatch(this::isOpenQuestion));
        TransformerAssistantMessageDTO question = sent.stream().filter(this::isOpenQuestion).findFirst().orElseThrow();
        assertEquals(HumanInTheLoopType.SELECT_COLUMN, question.getChatMessage().getHumanInTheLoop().getFirst().getType());
        // Columns that were not offered are ignored
        starter.message("session", userMessage("patient_nbr", List.of("unknown", "patient_nbr")));
        await().atMost(Duration.ofSeconds(5)).until(() -> hasType(TransformerAssistantMessageType.DONE));

        assertEquals(List.of("patient_nbr"), answers);
        List<ConnectorMappingDTO> mappings = starter.apply("session", List.of(1L)).getSchemaMapping();
        assertEquals(List.of("patient_nbr", "age"), mappings.stream().map(ConnectorMappingDTO::getColumn).toList());
        assertEquals("Unique Patient ID", mappings.getFirst().getMapping());
    }

    @Test
    void stopsARunThatWaitsForAnAnswer() {
        loop((events, user) -> answers.add(user.ask("Which column holds the age?")));
        starter.start("session", new ConnectorDTO(), "keycloak-user", sent::add);
        await().atMost(Duration.ofSeconds(5)).until(() -> sent.stream().anyMatch(this::isOpenQuestion));

        starter.stop("session");

        await().during(Duration.ofMillis(200)).atMost(Duration.ofSeconds(2)).until(() -> !hasType(TransformerAssistantMessageType.DONE));
        assertTrue(answers.isEmpty());
    }

    @SuppressWarnings("unchecked")
    private void loop(LoopBody body) {
        Answer<Void> answer = invocation -> {
            body.run(invocation.getArgument(2), invocation.getArgument(4));
            return null;
        };
        doAnswer(answer).when(starter.transformerLoop).run(any(ConnectorDTO.class), eq("keycloak-user"),
                any(Consumer.class), any(Supplier.class), any(TransformerAssistantUser.class));
    }

    private static TransformerAssistantMessageDTO userMessage(String text, List<String> columns) {
        TransformerAssistantMessageDTO message = new TransformerAssistantMessageDTO(TransformerAssistantMessageType.USER_MESSAGE);
        message.setMessage(text);
        message.setColumns(columns);
        return message;
    }

    private boolean isOpenQuestion(TransformerAssistantMessageDTO message) {
        return message.getType() == TransformerAssistantMessageType.CHAT_MESSAGE && !message.getChatMessage().isDone();
    }

    private boolean hasType(TransformerAssistantMessageType type) {
        return sent.stream().anyMatch(message -> message.getType() == type);
    }

    @FunctionalInterface
    private interface LoopBody {
        void run(Consumer<TransformerAssistantMessageDTO> events, TransformerAssistantUser user);
    }
}

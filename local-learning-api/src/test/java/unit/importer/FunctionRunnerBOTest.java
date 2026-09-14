package unit.importer;

import bio.cosy.feddb.local.api.importer.functions.FunctionRegistry;
import bio.cosy.feddb.local.api.importer.functions.FunctionRunnerBO;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.DateTimeToDateFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.builtin.SnomedGenderMapperFunction;
import bio.cosy.feddb.local.api.importer.transformer.ConnectorTransformerDTO;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.util.TypeLiteral;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FunctionRunnerBOTest {

    @Test
    void existsReturnsTrueForRegisteredFunction() {
        FunctionRunnerBO functionRunnerBO = createRunnerBO();

        assertTrue(functionRunnerBO.exists("builtin", "snomed_gender_mapper"));
        assertFalse(functionRunnerBO.exists("builtin", "does_not_exist"));
    }

    @Test
    void applyExecutesManagedMapperFunction() {
        FunctionRunnerBO functionRunnerBO = createRunnerBO();

        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setModuleName("builtin");
        transformer.setMethodName("snomed_gender_mapper");
        transformer.setColumn("gender");

        List<Map<String, Object>> result = functionRunnerBO.apply(
                transformer,
                List.of(row("gender", 248152002.0)),
                null, null
        );

        assertEquals("Female (finding)", result.get(0).get("gender"));
    }

    @Test
    void applyPassesHyperparamsToManagedFunctions() {
        FunctionRunnerBO functionRunnerBO = createRunnerBO();

        ConnectorTransformerDTO transformer = new ConnectorTransformerDTO();
        transformer.setModuleName("builtin");
        transformer.setMethodName("datetime_to_date");
        transformer.setColumn("value");
        transformer.setHyperparams(new LinkedHashMap<>(Map.of("datetime_format", "'%Y-%m-%d'")));

        List<Map<String, Object>> result = functionRunnerBO.apply(
                transformer,
                List.of(row("value", "2024-05-17")),
                null, null
        );

        assertEquals("2024-05-17", result.get(0).get("value"));
    }

    private static FunctionRunnerBO createRunnerBO() {
        return createRunnerBO(new DateTimeToDateFunction(), new SnomedGenderMapperFunction());
    }

    private static FunctionRunnerBO createRunnerBO(DateTimeToDateFunction dateTimeToDateFunction,
                                                   SnomedGenderMapperFunction snomedGenderMapperFunction) {
        FunctionRunnerBO bo = new FunctionRunnerBO();
        try {
            Field registryField = FunctionRunnerBO.class.getDeclaredField("registry");
            registryField.setAccessible(true);
            registryField.set(bo, new FunctionRegistry(new TestInstance(List.of())) {
                @Override
                public Optional<AbstractManagedFunction> get(String module, String methodName) {
                    if ("builtin".equals(module) && "datetime_to_date".equals(methodName)) {
                        return Optional.of(dateTimeToDateFunction);
                    }
                    if ("builtin".equals(module) && "snomed_gender_mapper".equals(methodName)) {
                        return Optional.of(snomedGenderMapperFunction);
                    }
                    return Optional.empty();
                }
            });
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        return bo;
    }

    private static Map<String, Object> row(String key, Object value) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static final class TestInstance implements Instance<AbstractManagedFunction> {

        private final List<AbstractManagedFunction> values;

        private TestInstance(List<AbstractManagedFunction> values) {
            this.values = values;
        }

        @Override
        public AbstractManagedFunction get() {
            return values.isEmpty() ? null : values.getFirst();
        }

        @Override
        public Instance<AbstractManagedFunction> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <U extends AbstractManagedFunction> Instance<U> select(Class<U> subtype, Annotation... qualifiers) {
            List<U> selected = values.stream()
                    .filter(subtype::isInstance)
                    .map(subtype::cast)
                    .toList();
            return new TypedTestInstance<>(selected);
        }

        @Override
        public <U extends AbstractManagedFunction> Instance<U> select(TypeLiteral<U> subtype, Annotation... qualifiers) {
            return new TypedTestInstance<>(Stream.<U>empty().toList());
        }

        @Override
        public boolean isUnsatisfied() {
            return values.isEmpty();
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public void destroy(AbstractManagedFunction instance) {
        }

        @Override
        public Instance.Handle<AbstractManagedFunction> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Instance.Handle<AbstractManagedFunction>> handles() {
            return List.of();
        }

        @Override
        public java.util.Iterator<AbstractManagedFunction> iterator() {
            return values.iterator();
        }
    }

    private static final class TypedTestInstance<U extends AbstractManagedFunction> implements Instance<U> {

        private final List<U> values;

        private TypedTestInstance(List<U> values) {
            this.values = values;
        }

        @Override
        public U get() {
            return values.isEmpty() ? null : values.getFirst();
        }

        @Override
        public Instance<U> select(Annotation... qualifiers) {
            return this;
        }

        @Override
        public <S extends U> Instance<S> select(Class<S> subtype, Annotation... qualifiers) {
            List<S> selected = values.stream()
                    .filter(subtype::isInstance)
                    .map(subtype::cast)
                    .toList();
            return new TypedTestInstance<>(selected);
        }

        @Override
        public <S extends U> Instance<S> select(TypeLiteral<S> subtype, Annotation... qualifiers) {
            return new TypedTestInstance<>(Stream.<S>empty().toList());
        }

        @Override
        public boolean isUnsatisfied() {
            return values.isEmpty();
        }

        @Override
        public boolean isAmbiguous() {
            return false;
        }

        @Override
        public void destroy(U instance) {
        }

        @Override
        public Instance.Handle<U> getHandle() {
            throw new UnsupportedOperationException();
        }

        @Override
        public Iterable<? extends Instance.Handle<U>> handles() {
            return List.of();
        }

        @Override
        public java.util.Iterator<U> iterator() {
            return values.iterator();
        }
    }
}

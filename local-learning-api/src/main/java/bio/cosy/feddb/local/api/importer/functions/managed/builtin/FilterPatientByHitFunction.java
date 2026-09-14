package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedPatientFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.PatientFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Keeps a patient only when at least one of its rows contains the configured hit. */
@ApplicationScoped
public class FilterPatientByHitFunction extends AbstractManagedPatientFunction {

    @Override
    public String methodName() {
        return "Filter Patient By Hit";
    }

    @Override
    public String description() {
        return "Keep all rows of a patient only when at least one row matches the configured hit value.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                new FunctionParameterDTO(
                        "value", FunctionParameterType.OBJECT, true, null,
                        "Row value to inspect; map this parameter to the source column.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "hit", FunctionParameterType.OBJECT, true, null,
                        "Value that must occur in at least one patient row.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "case_sensitive", FunctionParameterType.BOOLEAN, false, "false",
                        "Whether string matching is case-sensitive.", null,
                        FunctionParameterUsage.HYPERPARAMETER)
        );
    }

    @Override
    public List<Map<String, Object>> execute(PatientFunctionExecutionContext context) {
        if (!context.getParams().containsKey("hit")) {
            throw new IllegalArgumentException("'hit' is required");
        }
        Object hit = context.getParams().get("hit");
        boolean caseSensitive = Boolean.parseBoolean(
                String.valueOf(context.getParams().getOrDefault("case_sensitive", false))
        );

        boolean found = context.getRows().stream()
                .map(row -> row.get("value"))
                .anyMatch(value -> matches(value, hit, caseSensitive));

        if (!found) {
            return List.of();
        }
        return Collections.nCopies(context.getRows().size(), Map.of());
    }

    private boolean matches(Object value, Object hit, boolean caseSensitive) {
        if (value == null || hit == null) {
            return value == hit;
        }
        if (!(value instanceof CharSequence) && !(hit instanceof CharSequence) && value.equals(hit)) {
            return true;
        }

        String actual = BuiltInFunctionSupport.normalizeCodeKey(value).strip();
        String expected = BuiltInFunctionSupport.normalizeCodeKey(hit).strip();
        if (!caseSensitive) {
            actual = actual.toLowerCase(Locale.ROOT);
            expected = expected.toLowerCase(Locale.ROOT);
        }
        return actual.equals(expected);
    }
}

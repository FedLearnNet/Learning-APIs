package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Trim surrounding whitespace and optionally normalise the casing of a cell,
 * reversing a site whose export carries inconsistent casing and stray spaces
 * (a frequent symptom of spreadsheet-derived extracts).
 * <p>
 * {@code mode}:
 * <ul>
 *   <li>{@code NONE} (default) — trim only;</li>
 *   <li>{@code UPPER} / {@code LOWER} — trim and upper/lower-case;</li>
 *   <li>{@code TITLE} — trim and title-case each whitespace-separated word
 *       (first letter upper, the rest lower), collapsing internal runs of
 *       whitespace to a single space.</li>
 * </ul>
 */
@ApplicationScoped
public class TrimAndCaseFunction extends AbstractManagedCellFunction {

    @Override
    public String methodName() {
        return "Trim And Case";
    }

    @Override
    public String description() {
        return "Trim whitespace and normalise casing (NONE, UPPER, LOWER, TITLE) of a value.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("mode", FunctionParameterType.STRING, false, "NONE",
                        "Casing mode after trimming: NONE (trim only), UPPER, LOWER, or TITLE.")
        );
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Object value = context.getValue();
        if (value == null) {
            return null;
        }
        String trimmed = value.toString().strip();
        String mode = String.valueOf(context.getParams().getOrDefault("mode", "NONE")).toUpperCase();
        return switch (mode) {
            case "UPPER" -> trimmed.toUpperCase();
            case "LOWER" -> trimmed.toLowerCase();
            case "TITLE" -> titleCase(trimmed);
            default -> trimmed;
        };
    }

    private String titleCase(String value) {
        if (value.isEmpty()) {
            return value;
        }
        return Arrays.stream(value.split("\\s+"))
                .filter(word -> !word.isEmpty())
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1).toLowerCase())
                .collect(Collectors.joining(" "));
    }
}

package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Reads a date or datetime cell with one format and writes it with another, e.g. to
 * reduce a birth date to the birth year or to turn a local date notation into ISO.
 * Both formats use Python-style tokens: %Y year, %m month, %d day, %H hour,
 * %M minute, %S second. Examples:
 * <ul>
 *     <li>1975-06-23 with %Y-%m-%d to %Y gives 1975</li>
 *     <li>23.06.1975 with %d.%m.%Y to %Y-%m-%d gives 1975-06-23</li>
 *     <li>2024-05-17 13:45:00 with %Y-%m-%d %H:%M:%S to %d.%m.%Y %H:%M gives 17.05.2024 13:45</li>
 * </ul>
 * Values not matching the input format are also read as ISO datetime, e.g. 1975-06-23T08:30:00.
 * Blank values and values that cannot be read or written are left unchanged, so the
 * schema validation reports them.
 */
@ApplicationScoped
public class ReformatDateFunction extends AbstractManagedCellFunction {

    private static final String DEFAULT_FORMAT = "%Y-%m-%d";

    @Override
    public String methodName() {
        return "Reformat Date";
    }

    @Override
    public String description() {
        return "Read a date or datetime with the input format and write it with the output format "
                + "(Python-style, e.g. %Y-%m-%d to %Y turns 1975-06-23 into 1975). "
                + "Values that cannot be read are left unchanged.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("input_format", FunctionParameterType.STRING, false, DEFAULT_FORMAT,
                        "Format of the source value, e.g. %d.%m.%Y for 23.06.1975 or %Y-%m-%d %H:%M:%S for 1975-06-23 08:30:00."),
                FunctionParameterDTO.of("output_format", FunctionParameterType.STRING, false, DEFAULT_FORMAT,
                        "Format to write, e.g. %Y for 1975 or %Y-%m-%d for the ISO date 1975-06-23.")
        );
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Object value = context.getValue();
        if (BuiltInFunctionSupport.isBlank(value)) {
            return value;
        }
        Map<String, Object> params = context.getParams();
        try {
            DateTimeFormatter inputFormatter = BuiltInFunctionSupport.resolveFormatter(
                    params.getOrDefault("input_format", DEFAULT_FORMAT), DEFAULT_FORMAT);
            DateTimeFormatter outputFormatter = BuiltInFunctionSupport.resolveFormatter(
                    params.getOrDefault("output_format", DEFAULT_FORMAT), DEFAULT_FORMAT);
            LocalDateTime dateTime = BuiltInFunctionSupport.toDateTime(value, inputFormatter);
            if (dateTime == null) {
                return value;
            }
            return outputFormatter.format(dateTime);
        } catch (DateTimeException | IllegalArgumentException ignored) {
            return value;
        }
    }
}

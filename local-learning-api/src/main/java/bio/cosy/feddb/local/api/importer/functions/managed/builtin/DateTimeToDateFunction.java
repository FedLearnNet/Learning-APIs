package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;

@ApplicationScoped
public class DateTimeToDateFunction extends AbstractManagedCellFunction {

    @Override
    public String methodName() {
        return "datetime_to_date";
    }

    @Override
    public String description() {
        return "Convert a datetime string to an ISO local date string.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("datetime_format", FunctionParameterType.STRING, false, "%Y-%m-%d",
                        "Format pattern of the input datetime string (Python-style, e.g. %Y-%m-%d).")
        );
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Object value = context.getValue();
        if (!(value instanceof String text)) {
            return value;
        }

        var formatter = BuiltInFunctionSupport.resolveFormatter(
                context.getParams().getOrDefault("datetime_format", "%Y-%m-%d"),
                "%Y-%m-%d"
        );

        try {
            return LocalDateTime.parse(text, formatter).toLocalDate().toString();
        } catch (DateTimeParseException ignored) {
            try {
                return LocalDate.parse(text, formatter).toString();
            } catch (DateTimeParseException ignoredAgain) {
                return value;
            }
        }
    }
}

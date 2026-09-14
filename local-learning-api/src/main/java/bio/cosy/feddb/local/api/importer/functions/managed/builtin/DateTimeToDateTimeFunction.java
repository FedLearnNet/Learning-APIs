package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@ApplicationScoped
public class DateTimeToDateTimeFunction extends AbstractManagedCellFunction {

    @Override
    public String methodName() {
        return "datetime_to_datetime";
    }

    @Override
    public String description() {
        return "Convert a datetime string to an ISO datetime string.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("datetime_format", FunctionParameterType.STRING, false, "yyyy-MM-dd HH:mm:ss",
                        "Format pattern of the input datetime string (Java DateTimeFormatter pattern).")
        );
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Object value = context.getValue();
        if (!(value instanceof String text)) {
            return value;
        }

        String format = String.valueOf(
                context.getParams().getOrDefault("datetime_format", "yyyy-MM-dd HH:mm:ss")
        );

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(format);
        LocalDateTime dt = LocalDateTime.parse(text, formatter);

        return dt.atZone(ZoneId.systemDefault()).toOffsetDateTime().toString();
    }
}

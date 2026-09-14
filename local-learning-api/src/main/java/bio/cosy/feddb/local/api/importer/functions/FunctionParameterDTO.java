package bio.cosy.feddb.local.api.importer.functions;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class FunctionParameterDTO {
    private String name;
    private FunctionParameterType type;
    private boolean required;
    private String defaultValue;
    private String description;
    private List<String> choices;
    private FunctionParameterUsage usage = FunctionParameterUsage.INPUT;

    public FunctionParameterDTO(String name, FunctionParameterType type, boolean required, String defaultValue) {
        this(name, type, required, defaultValue, null, null);
    }

    public FunctionParameterDTO(String name,
                                FunctionParameterType type,
                                boolean required,
                                String defaultValue,
                                String description,
                                List<String> choices) {
        this(name, type, required, defaultValue, description, choices, FunctionParameterUsage.INPUT);
    }

    public FunctionParameterDTO(String name,
                                FunctionParameterType type,
                                boolean required,
                                String defaultValue,
                                String description,
                                List<String> choices,
                                FunctionParameterUsage usage) {
        this.name = name;
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
        this.description = description;
        this.choices = choices;
        this.usage = usage == null ? FunctionParameterUsage.INPUT : usage;
    }

    public static FunctionParameterDTO of(String name,
                                          FunctionParameterType type,
                                          boolean required,
                                          String defaultValue,
                                          String description) {
        return new FunctionParameterDTO(name, type, required, defaultValue, description, null);
    }
}

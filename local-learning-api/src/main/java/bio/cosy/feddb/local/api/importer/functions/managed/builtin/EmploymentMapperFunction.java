package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class EmploymentMapperFunction extends AbstractCodeMapperFunction {

    private static final Map<String, String> EMPLOYMENT_MAP = Map.ofEntries(
            Map.entry("1", "Worker / farmer / fisherman"),
            Map.entry("2", "Employee / religious / military"),
            Map.entry("3", "Self-employed / manager"),
            Map.entry("4", "Housewife"),
            Map.entry("5", "Student"),
            Map.entry("6", "Unemployed"),
            Map.entry("7", "Craftsman / trader"),
            Map.entry("8", "Driver"),
            Map.entry("9", "Pensioner"),
            Map.entry("10", "Other")
    );

    @Override
    public String methodName() {
        return "Employment Mapper";
    }

    @Override
    public String description() {
        return "Map employment codes to their labels.";
    }

    @Override
    protected Map<String, String> codeMap() {
        return EMPLOYMENT_MAP;
    }
}

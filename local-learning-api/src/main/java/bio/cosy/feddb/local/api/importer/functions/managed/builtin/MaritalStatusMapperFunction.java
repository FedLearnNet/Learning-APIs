package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class MaritalStatusMapperFunction extends AbstractCodeMapperFunction {

    private static final Map<String, String> MARITAL_STATUS_MAP = Map.of(
            "1", "Single",
            "2", "Married",
            "3", "Widowed",
            "4", "Separated / Divorced",
            "5", "Lives alone",
            "6", "Caregiver presence"
    );

    @Override
    public String methodName() {
        return "Marital Status Mapper";
    }

    @Override
    public String description() {
        return "Map marital status codes to their labels.";
    }

    @Override
    protected Map<String, String> codeMap() {
        return MARITAL_STATUS_MAP;
    }
}

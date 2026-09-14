package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class MinorityStatusMapperFunction extends AbstractCodeMapperFunction {

    private static final Map<String, String> MINORITY_STATUS_MAP = Map.of(
            "1", "Caucasian",
            "2", "African",
            "3", "Asian",
            "4", "Aboriginal",
            "5", "Amerindian"
    );

    @Override
    public String methodName() {
        return "Minority Status Mapper";
    }

    @Override
    public String description() {
        return "Map minority status codes to their labels.";
    }

    @Override
    protected Map<String, String> codeMap() {
        return MINORITY_STATUS_MAP;
    }
}

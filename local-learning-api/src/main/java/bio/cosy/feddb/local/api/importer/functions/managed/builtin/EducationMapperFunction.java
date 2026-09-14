package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class EducationMapperFunction extends AbstractCodeMapperFunction {

    private static final Map<String, String> EDUCATION_MAP = Map.of(
            "1", "Illiterate",
            "2", "Lower education",
            "3", "Higher education",
            "4", "University education",
            "5", "Needs cultural mediator"
    );

    @Override
    public String methodName() {
        return "Education Mapper";
    }

    @Override
    public String description() {
        return "Map education codes to their labels.";
    }

    @Override
    protected Map<String, String> codeMap() {
        return EDUCATION_MAP;
    }
}

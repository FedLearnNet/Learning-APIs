package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class SnomedGenderMapperFunction extends AbstractCodeMapperFunction {

    private static final Map<String, String> SNOMED_GENDER_MAP = Map.ofEntries(
            Map.entry("248152002", "Female (finding)"),
            Map.entry("248153007", "Male (finding)"),
            Map.entry("32570681000036106", "Indeterminate sex (finding)"),
            Map.entry("32570691000036108", "Intersex (finding)"),
            Map.entry("407374003", "Transsexual (finding)"),
            Map.entry("407377005", "Female-to-male transsexual (finding)"),
            Map.entry("407376001", "Female to male transsexual person on hormone therapy (finding)"),
            Map.entry("407379008", "Surgically transgendered transsexual, female-to-male (finding)"),
            Map.entry("407378000", "Surgically transgendered transsexual, male-to-female (finding)"),
            Map.entry("714186001", "Male to female transsexual person on hormone therapy (finding)")
    );

    @Override
    public String methodName() {
        return "Snomed Gender Mapper";
    }

    @Override
    public String description() {
        return "Map SNOMED gender codes to their labels.";
    }

    @Override
    protected Map<String, String> codeMap() {
        return SNOMED_GENDER_MAP;
    }
}

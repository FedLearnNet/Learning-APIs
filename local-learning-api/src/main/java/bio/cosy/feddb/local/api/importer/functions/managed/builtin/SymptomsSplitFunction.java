package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class SymptomsSplitFunction extends AbstractSplitFlagsFunction {

    static final String PARAM_COLUMN = "GI Symptoms input column";

    static final Map<String, String> INPUT_TO_OUTPUT = Map.of(
        "diarrhoea",   "diarrhoea",
        "constipation","constipation",
        "pr bleeding", "pr_bleeding",
        "pr_bleeding", "pr_bleeding",
        "bleeding",    "pr_bleeding"
    );

    static final List<String> RETURNKEYS = List.of("diarrhoea", "constipation", "pr_bleeding");
    static final Set<String> NO_VALUES = Set.of("none", "no");
    @Override public String methodName()  { return "GI Symptoms Split"; }
    @Override
    public String description() {
        return "Split a combined GI symptom column into individual symptom columns. " +
               "Allowed input values are: diarrhoea, constipation, pr bleeding/pr_bleeding/bleeding, no, none (all case insensitive). " +
               "Values may be combined with 'and', ',', or ';', e.g. 'diarrhoea and pr bleeding'. " +
               "Null/nil or empty values result in all columns being Unknown. " +
               "None/No results in all columns being No. " +
               "The function is case insensitive and trims whitespace, e.g. 'diarrhoea ' is valid";
    }

    @Override public String paramColumn() { return PARAM_COLUMN; }
    @Override public List<String> returnKeys() { return RETURNKEYS; }
    @Override public Set<String> noValues()    { return NO_VALUES; }
    @Override public Map<String, String> inputToOutput() { return INPUT_TO_OUTPUT; }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(FunctionParameterDTO.of(PARAM_COLUMN, FunctionParameterType.STRING, true, null,
                "The source column containing combined GI symptom values to split."));
    }

}

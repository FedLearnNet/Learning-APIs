package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ComorbidConditionsSplitFunction extends AbstractSplitFlagsFunction {

    static final String PARAM_COLUMN = "comorbid_conditions";

    static final Map<String, String> INPUT_TO_OUTPUT = Map.of(
        "diabetes",         "Diabetes Mellitus",
        "other metabolic",  "Metabolic Diseases",
        "cardiovascular",   "Cardiovascular Diseases",
        "respiratory",      "Respiration Disorders",
        "gastrointestinal", "Gastrointestinal Diseases",
        "renal",            "Kidney Diseases",
        "neurological",     "nervous system disorder",
        "other",            "Disease"
    );
    static final Set<String> NO_VALUES = Set.of("none", "no");

    @Override public String methodName()  { return "Comorbid Conditions Split"; }
    @Override
    public String description() {
        return "Split a combined comorbid conditions column into individual disease flag columns. " +
               "Accepted input tokens: diabetes, other metabolic, cardiovascular, respiratory, " +
               "gastrointestinal, renal, neurological, other, no, none (all case insensitive). " +
               "Multiple values may be joined with: 'and', ',', or ';'. " +
               "For example: 'diabetes and cardiovascular', 'renal, gastrointestinal'. " +
               "Null/nil or empty values result in all columns being Unknown. " +
               "No/None results in all columns being No.";
    }
    @Override public String paramColumn() { return PARAM_COLUMN; }
    @Override public Set<String> noValues()          { return NO_VALUES; }
    @Override public Map<String, String> inputToOutput() { return INPUT_TO_OUTPUT; }

    @Override
    public List<String> returnKeys() {
        return List.of(
            "Diabetes Mellitus", "Metabolic Diseases", "Cardiovascular Diseases",
            "Respiration Disorders", "Gastrointestinal Diseases", "Kidney Diseases",
            "nervous system disorder", "Disease"
        );
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(FunctionParameterDTO.of(PARAM_COLUMN, FunctionParameterType.STRING, true, null,
                "The source column containing combined comorbid condition values to split."));
    }
}

package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class LynchPolyposisSplitFunction extends AbstractSplitFlagsFunction {

    static final String PARAM_COLUMN = "Lynch/Polyposis syndrome Diagnosis input column";

    static final List<String> RETURNKEYS = List.of(
        "Lynch", "FAP", "SPS", "other Lynch/Polyposis-related diagnosis"
    );

    static final Set<String> NO_VALUES = Set.of("no", "none");

    static final Map<String, String> INPUT_TO_OUTPUT = Map.of(
        "lynch",                      "Lynch",
        "fap",                        "FAP",
        "sps",                        "SPS",
        "serrated polyposis syndrome","SPS",
        "other",                      "other Lynch/Polyposis-related diagnosis"
    );

    @Override public String methodName()  { return "Lynch/Polyposis Syndrome Split"; }
    @Override public String paramColumn() { return PARAM_COLUMN; }
    @Override public List<String> returnKeys()        { return RETURNKEYS; }
    @Override public Set<String> noValues()           { return NO_VALUES; }
    @Override public Map<String, String> inputToOutput() { return INPUT_TO_OUTPUT; }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(FunctionParameterDTO.of(PARAM_COLUMN, FunctionParameterType.STRING, true, null,
                "The source column containing Lynch/Polyposis syndrome diagnosis values to split."));
    }

    @Override
    public String description() {
        return "Split a column containing Lynch/Polyposis syndrome related categories into individual columns. " +
               "Allowed input values are: Lynch, FAP, SPS, Serrated polyposis Syndrome, other, no, none (all case insensitive). " +
               "Null/nil or empty values result in all columns being Unknown. " +
               "No/None results in all columns being No.";
    }
}

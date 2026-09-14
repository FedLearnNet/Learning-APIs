package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

/**
 * Turns a measured number into the clinical category a shared schema declares,
 * e.g. an HbA1c percentage into {@code Norm} / {@code >7} / {@code >8}, or a
 * serum glucose into {@code Norm} / {@code >200} / {@code >300}.
 * <p>
 * {@code bins} is an ordered JSON array of {@code {"lt": <bound>, "label":
 * "<category>"}} objects: the first bin whose bound is greater than the value
 * wins, and a value above every bound gets {@code else_label}. Because "not
 * measured" is itself a category in these schemas, a present-but-blank cell is
 * mapped to {@code empty_label} (e.g. {@code None}) rather than passed through,
 * while a cell the row does not have at all stays absent.
 */
@ApplicationScoped
public class NumericThresholdClassFunction extends AbstractManagedCellFunction {

    @Override
    public String methodName() {
        return "Numeric Threshold Class";
    }

    @Override
    public String description() {
        return "Classify a numeric value into ordered labelled bins, with a dedicated label for not-measured values.";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                FunctionParameterDTO.of("bins", FunctionParameterType.MAP, true, null,
                        "Ordered JSON array of {\"lt\": <upper bound, exclusive>, \"label\": \"<category>\"}."),
                FunctionParameterDTO.of("else_label", FunctionParameterType.STRING, true, null,
                        "Label for values at or above the last bound."),
                FunctionParameterDTO.of("empty_label", FunctionParameterType.STRING, false, "",
                        "Label for blank or non-numeric cells (e.g. 'None' for a test that was not performed).")
        );
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Map<String, Object> params = context.getParams();
        String emptyLabel = params.get("empty_label") == null ? "" : String.valueOf(params.get("empty_label"));

        if (context.getValue() == null) {
            // The column does not exist on this row at all (e.g. a row of a different
            // source table in a merged multi-table import). "Not measured" is only a
            // statement about rows that do carry the variable, so leave this one alone.
            return null;
        }

        Double numeric = BuiltInFunctionSupport.toDouble(context.getValue());
        if (numeric == null) {
            return emptyLabel;
        }

        List<Map<String, Object>> bins = BuiltInFunctionSupport.readJson(
                params.get("bins"), new TypeReference<List<Map<String, Object>>>() {
                }, "bins");
        if (bins == null || bins.isEmpty()) {
            throw new IllegalArgumentException("'bins' must contain at least one {lt,label} entry");
        }

        for (Map<String, Object> bin : bins) {
            Double bound = BuiltInFunctionSupport.toDouble(bin.get("lt"));
            if (bound == null) {
                throw new IllegalArgumentException("Every bin needs a numeric 'lt' bound: " + bin);
            }
            if (numeric < bound) {
                return String.valueOf(bin.get("label"));
            }
        }
        return String.valueOf(params.get("else_label"));
    }
}

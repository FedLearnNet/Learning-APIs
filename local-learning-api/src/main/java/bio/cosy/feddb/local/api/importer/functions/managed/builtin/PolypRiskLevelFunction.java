package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.FunctionParameterDTO;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterType;
import bio.cosy.feddb.local.api.importer.functions.FunctionParameterUsage;
import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedRowFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.RowFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Derives the post-polypectomy risk level from four colonoscopy polyp counts
 * (BSG/ACPGBI/PHE 2020). The guideline definitions, the classification rules and the
 * validation are documented in {@link #description()}, which is shown to users.
 */
@ApplicationScoped
public class PolypRiskLevelFunction extends AbstractManagedRowFunction {

    private static final String RETURN_KEY = "CRC risk according to BSG 2020";
    private static final String PREMALIGNANT = "premalignant";
    private static final String SERRATED = "serrated";
    private static final String ADVANCED_ADENOMATOUS = "advanced_adenomatous";
    private static final String ADVANCED_SERRATED = "advanced_serrated";
    private static final List<String> COUNT_INPUTS =
            List.of(PREMALIGNANT, SERRATED, ADVANCED_ADENOMATOUS, ADVANCED_SERRATED);

    @Override
    public String methodName() {
        return "Polyp Risk Level according to BSG 2020";
    }

    @Override
    public String description() {
        return """
                Derives the post-polypectomy risk level from the four consolidated colonoscopy \
                counts, following the BSG/ACPGBI/PHE surveillance guideline \
                (Rutter et al., Gut 2020;69:201, doi:10.1136/gutjnl-2019-319858).

                Each input counts the polyps of one guideline definition (quoted verbatim):
                - premalignant (premalignant polyp): "The term includes both serrated polyps \
                (excluding diminutive (1-5 mm) rectal hyperplastic polyps) and adenomatous polyps. \
                It does not include other polyps such as post-inflammatory polyps."
                - serrated (serrated polyp): "The umbrella term used to describe hyperplastic \
                polyps, sessile serrated lesions (SSLs), SSLs with dysplasia (SSLd), traditional \
                serrated adenomas (TSA) and mixed polyps."
                - advanced_adenomatous (advanced adenomatous polyp, synonymous with advanced \
                adenoma): "An adenoma of at least 10 mm in size or containing high-grade dysplasia. \
                Note: international definitions also include tubulovillous or villous histology, but \
                these are not part of the UK definition."
                - advanced_serrated (advanced serrated polyp): "A serrated polyp of at least \
                10 mm in size or containing any grade of dysplasia."
                Advanced colorectal polyp: "The term includes both advanced serrated polyps and \
                advanced adenomatous polyps."

                Advanced adenomatous and advanced serrated polyps are part of the premalignant \
                count, advanced serrated polyps are part of the serrated count. The serrated count \
                may exceed the premalignant count, as it includes diminutive rectal hyperplastic polyps.

                Classification:
                - high: at least 5 premalignant polyps, or at least 2 premalignant polyps \
                including at least one advanced colorectal polyp (the guideline's high-risk findings)
                - very low: no premalignant polyps
                - low: everything else

                Rows where all four counts are blank yield no risk, assuming no colonoscopy data \
                to be present. The run fails if only some counts are blank, a count is not a \
                non-negative whole number, advanced serrated exceeds serrated, or advanced \
                adenomatous plus advanced serrated exceeds premalignant.""";
    }

    @Override
    public List<FunctionParameterDTO> parameters() {
        return List.of(
                new FunctionParameterDTO(
                        PREMALIGNANT, FunctionParameterType.OBJECT, true, null,
                        "Total number of premalignant polyps (adenomas + serrated polyps).", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        SERRATED, FunctionParameterType.OBJECT, true, null,
                        "Number of serrated polyps, including diminutive rectal hyperplastic polyps.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        ADVANCED_ADENOMATOUS, FunctionParameterType.OBJECT, true, null,
                        "Number of advanced adenomas (>= 10 mm or high-grade dysplasia).", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        ADVANCED_SERRATED, FunctionParameterType.OBJECT, true, null,
                        "Number of advanced serrated polyps (>= 10 mm or any dysplasia), part of the serrated count.", null,
                        FunctionParameterUsage.INPUT),
                new FunctionParameterDTO(
                        "high_label", FunctionParameterType.STRING, false, "High",
                        "Text written for high-risk findings.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "low_label", FunctionParameterType.STRING, false, "Low",
                        "Text written for premalignant polyps without high-risk findings.", null,
                        FunctionParameterUsage.HYPERPARAMETER),
                new FunctionParameterDTO(
                        "very_low_label", FunctionParameterType.STRING, false, "Very Low",
                        "Text written when no premalignant polyps were found.", null,
                        FunctionParameterUsage.HYPERPARAMETER)
        );
    }

    @Override
    public List<String> returnKeys() {
        return List.of(RETURN_KEY);
    }

    @Override
    public Map<String, Object> execute(RowFunctionExecutionContext context) {
        Map<String, Object> row = context.getRow();
        Map<String, Object> params = context.getParams();

        Map<String, Object> result = new HashMap<>();
        if (COUNT_INPUTS.stream().allMatch(key -> BuiltInFunctionSupport.isBlank(row.get(key)))) {
            result.put(RETURN_KEY, null); // no colonoscopy data on this row
            return result;
        }

        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String key : COUNT_INPUTS) {
            counts.put(key, readCount(row, key));
        }
        int premalignant = counts.get(PREMALIGNANT);
        int serrated = counts.get(SERRATED);
        int advancedAdenomatous = counts.get(ADVANCED_ADENOMATOUS);
        int advancedSerrated = counts.get(ADVANCED_SERRATED);

        if (advancedSerrated > serrated) {
            throw new IllegalArgumentException("Inconsistent polyp counts: advanced serrated polyps ("
                    + advancedSerrated + ") exceed serrated polyps (" + serrated + "): " + counts);
        }
        if (advancedAdenomatous + advancedSerrated > premalignant) {
            throw new IllegalArgumentException("Inconsistent polyp counts: advanced adenomatous ("
                    + advancedAdenomatous + ") plus advanced serrated (" + advancedSerrated
                    + ") polyps exceed premalignant polyps (" + premalignant + "): " + counts);
        }

        String label;
        if (premalignant >= 5 || (advancedAdenomatous + advancedSerrated >= 1 && premalignant >= 2)) {
            label = labelParam(params, "high_label", "High");
        } else if (premalignant == 0) {
            label = labelParam(params, "very_low_label", "Very Low");
        } else {
            label = labelParam(params, "low_label", "Low");
        }
        result.put(RETURN_KEY, label);
        return result;
    }

    private static int readCount(Map<String, Object> row, String key) {
        Object raw = row.get(key);
        if (BuiltInFunctionSupport.isBlank(raw)) {
            throw new IllegalArgumentException("Missing polyp count '" + key
                    + "' while other counts are present; fill it (e.g. with 0) before classifying. Row: " + row);
        }
        Double value = BuiltInFunctionSupport.toDouble(raw);
        if (value == null || value < 0 || value != Math.floor(value) || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Polyp count '" + key
                    + "' must be a non-negative whole number, got: " + raw);
        }
        return value.intValue();
    }

    private static String labelParam(Map<String, Object> params, String key, String fallback) {
        Object value = params.get(key);
        return BuiltInFunctionSupport.isBlank(value) ? fallback : String.valueOf(value);
    }
}

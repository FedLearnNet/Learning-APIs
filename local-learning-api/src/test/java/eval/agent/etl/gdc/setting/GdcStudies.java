package eval.agent.etl.gdc.setting;

import java.util.Arrays;
import java.util.List;

/**
 * The ten studies of GDC-SM: The GDC Schema Matching Benchmark (https://doi.org/10.5281/zenodo.14963587, CC BY 4.0),
 * mapped onto the global schema of its 78 GDC fields (dAIbetes meta: example-data/gdc-sm/create_schema.sh). The tables
 * are downloaded with download-gdc-sm.sh.
 */
public final class GdcStudies {

    public static final String ROOT = "/eval/gdc";
    // Root ids of datamodeler-imports: the 78 fields of the ground truth, and all 736 GDC fields, the target of the benchmark
    private static final String GDC_SCHEMA_ID = "5e9db229-c847-50b6-925f-78c9681faed9";
    private static final String FULL_GDC_SCHEMA_ID = "9b722d97-3195-5a23-ab68-4bdf33028c41";

    public static final MatchingBenchmark BENCHMARK = new MatchingBenchmark("GDC schema matching benchmark", "gdc-benchmark",
            "The ten studies of GDC-SM (https://doi.org/10.5281/zenodo.14963587) are mapped by the assistant onto the GDC "
                    + "schema (the 78 fields of the ground truth, or all 736 like the benchmark).", "GDC", false);

    public static final List<GdcStudy> STUDIES = List.of(
            new GdcStudy("GDC Cao", "Cao.csv", "case_id"),
            new GdcStudy("GDC Clark", "Clark.csv", "Case_ID"),
            new GdcStudy("GDC Dou", "Dou.csv", "Proteomics_Participant_ID"),
            new GdcStudy("GDC Gillette", "Gillette.csv", "Participant"),
            new GdcStudy("GDC Huang", "Huang.csv", "case_id"),
            new GdcStudy("GDC Krug", "Krug.csv", "Sample.ID"),
            new GdcStudy("GDC McDermott", "McDermott.csv", "CPTAC Case ID"),
            new GdcStudy("GDC Satpathy", "Satpathy.csv", "Participant"),
            new GdcStudy("GDC Vasaikar", "Vasaikar.csv", "SampleID"),
            new GdcStudy("GDC Wang", "Wang.csv", "case_id"));

    private GdcStudies() {
    }

    /** The GDC schema of the dev data modeler, -Deval.gdc.schema=full for all fields; another installation passes its id with -Deval.gdc.schema-id. */
    public static String schemaId() {
        return System.getProperty("eval.gdc.schema-id",
                "full".equals(System.getProperty("eval.gdc.schema")) ? FULL_GDC_SCHEMA_ID : GDC_SCHEMA_ID);
    }

    public static List<GdcStudy> selected() {
        return selected(STUDIES);
    }

    /** The studies -Deval.datasets names, all without it. */
    public static List<GdcStudy> selected(List<GdcStudy> studies) {
        String names = System.getProperty("eval.datasets", "");
        if (names.isBlank()) {
            return studies;
        }
        List<String> selected = Arrays.stream(names.split(",")).map(String::strip).toList();
        return studies.stream().filter(study -> selected.contains(study.getName())).toList();
    }
}

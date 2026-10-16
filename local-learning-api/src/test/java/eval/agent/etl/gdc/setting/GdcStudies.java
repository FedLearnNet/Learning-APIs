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
    private static final String GDC_SCHEMA_ID = "6e918fea-0367-42a4-9666-6acbec996559";

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

    /** The GDC schema of the dev data modeler; another installation passes its id with -Deval.gdc.schema-id. */
    public static String schemaId() {
        return System.getProperty("eval.gdc.schema-id", GDC_SCHEMA_ID);
    }

    public static List<GdcStudy> selected() {
        String names = System.getProperty("eval.datasets", "");
        if (names.isBlank()) {
            return STUDIES;
        }
        List<String> selected = Arrays.stream(names.split(",")).map(String::strip).toList();
        return STUDIES.stream().filter(study -> selected.contains(study.getName())).toList();
    }
}

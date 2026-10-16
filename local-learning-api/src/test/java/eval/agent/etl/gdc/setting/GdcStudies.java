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

    public static final List<GdcStudy> STUDIES = List.of(
            new GdcStudy("GDC Cao", "Cao.csv", null),
            new GdcStudy("GDC Clark", "Clark.csv", null),
            new GdcStudy("GDC Dou", "Dou.csv", null),
            new GdcStudy("GDC Gillette", "Gillette.csv", null),
            new GdcStudy("GDC Huang", "Huang.csv", null),
            new GdcStudy("GDC Krug", "Krug.csv", null),
            new GdcStudy("GDC McDermott", "McDermott.csv", null),
            new GdcStudy("GDC Satpathy", "Satpathy.csv", null),
            new GdcStudy("GDC Vasaikar", "Vasaikar.csv", null),
            new GdcStudy("GDC Wang", "Wang.csv", null));

    private GdcStudies() {
    }

    /** The id of the GDC schema in the global data modeler, which differs per installation. */
    public static String schemaId() {
        String id = System.getProperty("eval.gdc.schema-id", "");
        if (id.isBlank()) {
            throw new IllegalStateException("Set -Deval.gdc.schema-id to the id of the uploaded GDC schema");
        }
        return id;
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

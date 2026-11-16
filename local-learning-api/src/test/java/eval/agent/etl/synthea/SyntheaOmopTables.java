package eval.agent.etl.synthea;

import eval.agent.etl.gdc.setting.GdcStudies;
import eval.agent.etl.gdc.setting.GdcStudy;
import eval.agent.etl.gdc.setting.MatchingBenchmark;

import java.util.List;

/**
 * The twelve Synthea tables of the Synthea-OMOP benchmark of OMAP (SMAT, Zhang et al. 2021,
 * https://github.com/JZCS2018/SMAT), mapped onto the 21 OMOP CDM tables OMAP matches them to (dAIbetes meta:
 * example-data/synthea-omop/create_schema.sh). Four tables have no match, so every field filled from them is an extra.
 * The tables are downloaded with download-synthea-omop.sh.
 */
public final class SyntheaOmopTables {

    public static final String ROOT = "/eval/synthea-omop";
    // Root id of datamodeler-imports/schemas/2026-10synthea_omop_schema.json
    private static final String SCHEMA_ID = "95fae617-4fb5-5847-a811-e8fc2812afe3";

    public static final MatchingBenchmark BENCHMARK = new MatchingBenchmark("Synthea-OMOP schema matching benchmark",
            "synthea-omop-benchmark", "The twelve Synthea tables of OMAP (https://github.com/JZCS2018/SMAT, Synthea "
            + "sample data of April 2020) are mapped by the assistant onto the 267 fields of the 21 OMOP CDM tables OMAP "
            + "matches them to; a field is named with its table.", "OMOP CDM", true);

    public static final List<GdcStudy> TABLES = List.of(
            table("allergies", "PATIENT"),
            table("careplans", "PATIENT"),
            table("conditions", "PATIENT"),
            table("encounters", "PATIENT"),
            table("imaging_studies", "PATIENT"),
            table("immunizations", "PATIENT"),
            table("medications", "PATIENT"),
            table("observations", "PATIENT"),
            table("organizations", "Id"),
            table("patients", "Id"),
            table("procedures", "PATIENT"),
            table("providers", "Id"));

    private SyntheaOmopTables() {
    }

    /** The OMOP schema of the data modeler; another installation passes its id with -Deval.synthea.schema-id. */
    public static String schemaId() {
        return System.getProperty("eval.synthea.schema-id", SCHEMA_ID);
    }

    public static List<GdcStudy> selected() {
        return GdcStudies.selected(TABLES);
    }

    private static GdcStudy table(String name, String caseIdColumn) {
        return new GdcStudy(ROOT, "Synthea " + name, name + ".csv", caseIdColumn);
    }
}

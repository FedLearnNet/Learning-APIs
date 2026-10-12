package eval.agent.etl.transform.setting;

import java.util.Arrays;
import java.util.List;

public final class TransformerWriterDatasets {

    private static final String US_130_SCHEMA_ID = "c4877033-3619-47c2-bf7d-37f48a2934b3";

    public static final List<TransformerWriterDataset> DATASETS = List.of(
            new TransformerWriterDataset(US_130_SCHEMA_ID, "US 130 Site 2", "/connectors/site_02.csv", ",", "patient_nbr", null),
            new TransformerWriterDataset(US_130_SCHEMA_ID, "US 130 Site 3", "/connectors/site_03.csv", ";", "patient_nbr", null),
            new TransformerWriterDataset(US_130_SCHEMA_ID, "US 130 Site 4", "/connectors/site_04.csv", ",", "patient_nbr", null),
            new TransformerWriterDataset(US_130_SCHEMA_ID, "US 130 Site 5", "/connectors/site_05.csv", ",", "patient_nbr", null),
            mimic());

    public static List<TransformerWriterDataset> selected() {
        String names = System.getProperty("eval.datasets", "");
        if (names.isBlank()) {
            return DATASETS;
        }
        List<String> selected = Arrays.stream(names.split(",")).map(String::strip).toList();
        return DATASETS.stream().filter(dataset -> selected.contains(dataset.getName())).toList();
    }

    private static TransformerWriterDataset mimic() {
        TransformerWriterDataset mimic = new TransformerWriterDataset(US_130_SCHEMA_ID, "MIMIC IV Demo",
                "/connectors/mimic-iv-demo-2.2.zip", ",", "subject_id", null);
        mimic.setMergeConfig("subject_id", "admissions", "diagnoses_icd", "labevents", "omr", "patients",
                "prescriptions", "procedures_icd", "services");
        return mimic;
    }
}

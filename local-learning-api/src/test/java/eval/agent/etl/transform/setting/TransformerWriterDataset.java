package eval.agent.etl.transform.setting;

import bio.cosy.feddb.local.api.importer.extract.SheetMergeResultDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class TransformerWriterDataset {
    private String schemaId;
    private String name;
    private String dataPath;
    private String delimiter;
    private String patientIdColumn;
    private SheetMergeResultDTO merge;

    public void setMergeConfig(String patientColumn, String... tables) {
        Map<String, String> columnPerTable = List.of(tables).stream()
                .collect(Collectors.toMap(table -> table, table -> patientColumn));
        this.merge = new SheetMergeResultDTO(patientColumn, columnPerTable, patientColumn);
    }
}

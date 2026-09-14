package bio.cosy.feddb.local.api.importer.files;


import bio.cosy.feddb.core.api.file.analytics.ColumnProfile;
import bio.cosy.feddb.local.api.importer.files.table.TableData;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConnectorFileUploadInfoDTO {
    public static final String DEFAULT_SHEET_NAME = "0";
    private String sheet;
    private String json;
    private List<String> columns;
    private List<String> renamedColumns;
    private List<Boolean> deletedColumns;
    private List<ColumnProfile> columnProfiles;

    public ConnectorFileUploadInfoDTO(String sheet, TableData tableData, String json) {
        this.sheet = sheet;
        this.columns = tableData.getColumns();
        this.renamedColumns = new ArrayList<>(tableData.getColumns());
        this.deletedColumns = tableData.getColumns().stream().map(col -> false).toList();
        this.json = json;
    }

    public ConnectorFileUploadInfoDTO(TableData tableData, String json) {
        this.sheet = ConnectorFileUploadInfoDTO.DEFAULT_SHEET_NAME;
        this.columns = tableData.getColumns();
        this.renamedColumns = new ArrayList<>(tableData.getColumns());
        this.deletedColumns = tableData.getColumns().stream().map(col -> false).toList();
        this.json = json;
    }
}

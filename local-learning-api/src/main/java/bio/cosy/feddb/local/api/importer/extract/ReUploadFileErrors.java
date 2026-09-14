package bio.cosy.feddb.local.api.importer.extract;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReUploadFileErrors {
    private List<String> missingColumns;
    private List<String> notFoundColumns;
}

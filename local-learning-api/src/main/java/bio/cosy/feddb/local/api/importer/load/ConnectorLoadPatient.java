package bio.cosy.feddb.local.api.importer.load;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConnectorLoadPatient {
    @NotNull
    private String externalPatientId;
    @NotNull
    private List<ConnectorLoadRow> rows = new ArrayList<>();
}

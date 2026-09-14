package bio.cosy.feddb.core.api.project;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SelectedDataIdsDTO {
    @NotBlank(message = "Global Ontology ID must not be blank")
    private String globalOntologyId;
    @NotBlank(message = "Global Data Type ID must not be blank")
    private String globalDataTypeId;

    public String toUniqueKey() {
        return globalOntologyId + "@" + globalDataTypeId;
    }

    @Override
    public String toString() {
        return "SelectedDataIdsDTO{" +
                "globalOntologyId='" + globalOntologyId + '\'' +
                ", globalDataTypeId='" + globalDataTypeId + '\'' +
                '}';
    }


    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        SelectedDataIdsDTO that = (SelectedDataIdsDTO) o;
        return Objects.equals(getGlobalOntologyId(), that.getGlobalOntologyId()) && Objects.equals(getGlobalDataTypeId(), that.getGlobalDataTypeId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getGlobalOntologyId(), getGlobalDataTypeId());
    }
}

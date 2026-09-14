package bio.cosy.feddb.core.api.app.config;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ToolConfigsDTO {
    private List<ToolHyperParamConfigDTO> hyperparams = new ArrayList<>();
    private List<ToolInputConfigDTO> input = new ArrayList<>();
    private List<ToolOutputConfigDTO> output = new ArrayList<>();

    public boolean isEqualTo(ToolConfigsDTO other) {
        if (other == null) return false;
        if (this.hyperparams == null && other.hyperparams != null) return false;
        if (this.hyperparams != null && other.hyperparams == null) return false;
        if (this.input == null && other.input != null) return false;
        if (this.input != null && other.input == null) return false;
        if (this.output == null && other.output != null) return false;
        if (this.output != null && other.output == null) return false;

        if (this.hyperparams != null) {
            if (this.hyperparams.size() != other.hyperparams.size()) return false;
        }
        if (this.input != null) {
            if (this.input.size() != other.input.size()) return false;
        }
        if (this.output != null) {
            if (this.output.size() != other.output.size()) return false;
        }

        if (this.hyperparams != null) {
            for (int i = 0; i < this.hyperparams.size(); i++) {
                if (this.hyperparams.get(i) == null && other.hyperparams.get(i) != null) return false;
                if (this.hyperparams.get(i) != null && !this.hyperparams.get(i).equals(other.hyperparams.get(i)))
                    return false;
            }
        }

        if (this.input != null) {
            for (int i = 0; i < this.input.size(); i++) {
                if (this.input.get(i) == null && other.input.get(i) != null) return false;
                if (this.input.get(i) != null && !this.input.get(i).equals(other.input.get(i))) return false;
            }
        }

        if (this.output != null) {
            for (int i = 0; i < this.output.size(); i++) {
                if (this.output.get(i) == null && other.output.get(i) != null) return false;
                if (this.output.get(i) != null && !this.output.get(i).equals(other.output.get(i))) return false;
            }
        }

        return true;
    }
}

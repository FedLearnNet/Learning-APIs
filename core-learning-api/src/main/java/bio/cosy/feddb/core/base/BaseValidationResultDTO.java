package bio.cosy.feddb.core.base;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BaseValidationResultDTO {
    private boolean ok;
    private List<String> errors;
    private Map<String, Object> meta = new HashMap<>();

    public BaseValidationResultDTO(boolean ok, List<String> errors) {
        this.ok = ok;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static BaseValidationResultDTO ok() {
        return new BaseValidationResultDTO(true, List.of());
    }

    public static BaseValidationResultDTO fail(List<String> errors) {
        return new BaseValidationResultDTO(false, errors == null ? List.of() : errors);
    }

    public static BaseValidationResultDTO fail(List<String> errors, Map<String, Object> meta) {
        BaseValidationResultDTO failed = fail(errors);
        failed.setMeta(meta == null ? Map.of() : Map.copyOf(meta));
        return failed;
    }

    public static BaseValidationResultDTO fail(String error) {
        return new BaseValidationResultDTO(false, error == null ? List.of() : List.of(error));
    }
}


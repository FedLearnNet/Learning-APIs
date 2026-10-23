package bio.cosy.feddb.core.execution.hyperparam.config;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.random.RandomGenerator;

/**
 * The values one hyper parameter may take during a search. Which fields are read depends on the
 * type: {@code value} for FIXED, {@code values} for CHOICE, {@code low} and {@code high} otherwise.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParamDomainDTO {

    @NotNull
    private ParamDomainType type;
    private Object value;
    private List<Object> values;
    private Double low;
    private Double high;

    public static ParamDomainDTO fixed(Object value) {
        return new ParamDomainDTO(ParamDomainType.FIXED, value, null, null, null);
    }

    public static ParamDomainDTO choice(List<?> values) {
        return new ParamDomainDTO(ParamDomainType.CHOICE, null, new ArrayList<>(values), null, null);
    }

    public static ParamDomainDTO intRange(long low, long high) {
        return new ParamDomainDTO(ParamDomainType.INT_RANGE, null, null, (double) low, (double) high);
    }

    public static ParamDomainDTO uniform(double low, double high) {
        return new ParamDomainDTO(ParamDomainType.UNIFORM, null, null, low, high);
    }

    public static ParamDomainDTO logUniform(double low, double high) {
        return new ParamDomainDTO(ParamDomainType.LOG_UNIFORM, null, null, low, high);
    }

    @JsonIgnore
    @AssertTrue(message = "Domain values do not match its type: a choice needs values, ranges need low <= high, log-uniform a positive low")
    public boolean isValid() {
        if (type == null) {
            return true; // Reported by @NotNull.
        }
        return switch (type) {
            case FIXED -> true;
            case CHOICE -> values != null && !values.isEmpty();
            case INT_RANGE -> hasBounds() && low == Math.rint(low) && high == Math.rint(high) && low <= high;
            case UNIFORM -> hasBounds() && low < high;
            case LOG_UNIFORM -> hasBounds() && low > 0 && low < high;
        };
    }

    private boolean hasBounds() {
        return low != null && high != null && Double.isFinite(low) && Double.isFinite(high);
    }

    /** Number of values, empty for a continuous domain. */
    public OptionalLong size() {
        return switch (type) {
            case FIXED -> OptionalLong.of(1);
            case CHOICE -> OptionalLong.of(values.size());
            case INT_RANGE -> OptionalLong.of(high.longValue() - low.longValue() + 1);
            case UNIFORM, LOG_UNIFORM -> OptionalLong.empty();
        };
    }

    /** Value at an index below {@link #size()}. */
    public Object valueAt(long index) {
        return switch (type) {
            case FIXED -> value;
            case CHOICE -> values.get(Math.toIntExact(index));
            case INT_RANGE -> low.longValue() + index;
            case UNIFORM, LOG_UNIFORM -> throw new UnsupportedOperationException(type + " cannot be enumerated");
        };
    }

    public Object sample(RandomGenerator random) {
        return switch (type) {
            case FIXED -> value;
            case CHOICE -> values.get(random.nextInt(values.size()));
            case INT_RANGE -> low.longValue() + random.nextLong(size().getAsLong());
            case UNIFORM -> random.nextDouble(low, high);
            case LOG_UNIFORM -> Math.exp(random.nextDouble(Math.log(low), Math.log(high)));
        };
    }
}

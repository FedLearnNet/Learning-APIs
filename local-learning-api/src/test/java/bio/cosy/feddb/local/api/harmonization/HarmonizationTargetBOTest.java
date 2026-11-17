package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.local.api.schema.datatype.DataTypeDTO;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The reference is a sample: a value columns of the same kind show is allowed in all of them. */
class HarmonizationTargetBOTest {

    private static final Map<String, Set<Object>> OBSERVED = Map.of(
            "tolbutamide", Set.of("No"),
            "metformin", new LinkedHashSet<>(List.of("No", "Steady", "Up", "Down")),
            "gender", Set.of("Female", "Male"));

    @Test
    void allowsTheValuesOfColumnsOfTheSameKind() {
        HarmonizationTargetDTO tolbutamide = target("tolbutamide", "No");

        HarmonizationTargetBO.addValuesOfTheSameKind(tolbutamide, List.of("metformin"), OBSERVED);

        assertEquals(List.of("No", "Steady", "Up", "Down"), List.copyOf(tolbutamide.getDataType().getAllowedValues()));
        assertTrue(tolbutamide.getDataType().getDescription().endsWith("also hold [Steady, Up, Down], so these values are allowed as well."),
                tolbutamide.getDataType().getDescription());
    }

    @Test
    void ignoresAColumnWithoutAValueInCommon() {
        HarmonizationTargetDTO tolbutamide = target("tolbutamide", "No");

        HarmonizationTargetBO.addValuesOfTheSameKind(tolbutamide, List.of("gender", "unknown"), OBSERVED);

        assertEquals(Set.of("No"), tolbutamide.getDataType().getAllowedValues());
    }

    private static HarmonizationTargetDTO target(String name, String... values) {
        DataTypeDTO dataType = new DataTypeDTO();
        dataType.setDescription("Values of the reference file.");
        dataType.setAllowedValues(new LinkedHashSet<>(List.of(values)));
        HarmonizationTargetDTO target = new HarmonizationTargetDTO();
        target.setName(name);
        target.setDataType(dataType);
        return target;
    }
}

package bio.cosy.feddb.core.api.datamodler.datatype;

import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationDTO;
import bio.cosy.feddb.core.api.datamodler.validation.DataTypeValidationType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataTypeValueGeneratorTest {

    @Test
    void picksOneOfTheOptions() {
        DataTypeNodeDTO dataType = dataType(DataTypes.CATEGORICAL);
        dataType.setOptions(List.of("Yes", "No"));

        assertTrue(List.of("Yes", "No").contains(DataTypeValueGenerator.randomValue(dataType, new Random())));
    }

    @Test
    void staysWithinTheMinAndMaxValidations() {
        DataTypeNodeDTO dataType = dataType(DataTypes.INT);
        dataType.setValidations(List.of(validation(DataTypeValidationType.MIN, "18"), validation(DataTypeValidationType.MAX, "20")));

        for (int run = 0; run < 50; run++) {
            int value = (int) DataTypeValueGenerator.randomValue(dataType, new Random(run));
            assertTrue(value >= 18 && value <= 20, "value " + value);
        }
    }

    @Test
    void writesDatesInIsoFormat() {
        LocalDate.parse((String) DataTypeValueGenerator.randomValue(dataType(DataTypes.DATE), new Random()));
    }

    @Test
    void keepsTheShapeOfAValue() {
        String like = DataTypeValueGenerator.randomLike("AB-12 x", new Random(1));

        assertTrue(like.matches("[A-Z]{2}-\\d{2} [a-z]"), like);
        assertNotEquals("AB-12 x", like);
    }

    @Test
    void givesTheSameValuesForTheSameSeed() {
        assertEquals(DataTypeValueGenerator.randomLike("AB-12", new Random(7)), DataTypeValueGenerator.randomLike("AB-12", new Random(7)));
    }

    private static DataTypeNodeDTO dataType(DataTypes type) {
        DataTypeNodeDTO dataType = new DataTypeNodeDTO();
        dataType.setType(type);
        return dataType;
    }

    private static DataTypeValidationDTO validation(DataTypeValidationType type, String validator) {
        DataTypeValidationDTO validation = new DataTypeValidationDTO();
        validation.setName(type);
        validation.setValidator(validator);
        return validation;
    }
}

package bio.cosy.feddb.local.api.query.builder;

import bio.cosy.feddb.core.api.datamodler.datatype.DataTypes;
import bio.cosy.feddb.core.api.query.QueryRejectedException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryValueHelperTest {

    @Test
    void numbers() {
        assertTrue(QueryValueHelper.isNumber("42"));
        assertTrue(QueryValueHelper.isNumber(" -1.5 "));
        assertFalse(QueryValueHelper.isNumber(".5"));
        assertFalse(QueryValueHelper.isNumber("1e5"));
        assertFalse(QueryValueHelper.isNumber("abc"));
        assertFalse(QueryValueHelper.isNumber(null));
    }

    @Test
    void dataTypes() {
        assertTrue(QueryValueHelper.isText(DataTypes.CATEGORICAL));
        assertFalse(QueryValueHelper.isText(DataTypes.INT));
        assertTrue(QueryValueHelper.isNumericReadable(DataTypes.STRING));
        assertFalse(QueryValueHelper.isNumericReadable(DataTypes.DATE));
    }

    @Test
    void values() {
        assertEquals("x", QueryValueHelper.requireValue("x"));
        assertThrows(QueryRejectedException.class, () -> QueryValueHelper.requireValue(" "));
    }
}

package unit;

import bio.cosy.feddb.local.api.privacy.PrivacyBO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PrivacyBOTest {

    @Test
    void modifyQueryCountKeepsDefaultMinCountAtOneHundred() {
        PrivacyBO bo = new PrivacyBO();

        Assertions.assertEquals(0L, bo.modifyQueryCount(99L));
        Assertions.assertEquals(100L, bo.modifyQueryCount(100L));
    }

    @Test
    void modifyQueryCountUsesConfiguredMinCount() throws Exception {
        PrivacyBO bo = new PrivacyBO();
        injectField(bo, "config", config(25));

        Assertions.assertEquals(0L, bo.modifyQueryCount(24L));
        Assertions.assertEquals(25L, bo.modifyQueryCount(25L));
    }

    private static FLNetClientConfig config(int minCount) {
        FLNetClientConfig config = mock(FLNetClientConfig.class);
        FLNetClientConfig.PrivacyConfig privacyConfig = mock(FLNetClientConfig.PrivacyConfig.class);
        FLNetClientConfig.QueryPrivacyConfig queryConfig = mock(FLNetClientConfig.QueryPrivacyConfig.class);

        when(config.privacy()).thenReturn(privacyConfig);
        when(privacyConfig.query()).thenReturn(queryConfig);
        when(queryConfig.minCount()).thenReturn(minCount);
        return config;
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}

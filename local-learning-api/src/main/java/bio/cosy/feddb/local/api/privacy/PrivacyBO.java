package bio.cosy.feddb.local.api.privacy;

import bio.cosy.feddb.local.config.FLNetClientConfig;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PrivacyBO {

    @Inject
    FLNetClientConfig config;

    public Long modifyQueryCount(Long count) {
        return modifyCount(count, queryMinCount());
    }

    public int modifyQueryCount(int count) {
        return modifyCount((long) count, queryMinCount()).intValue();
    }

    /**
     * Modifies the count according to the privacy policy.
     * This privacy policy is defined as follows:
     * If the count is smaller than 100, the count is set to 0.
     * For count between 100 and 1000, we ceil to the nearest 3 digit number (123 -> 200)
     * For all other counts:
     * If the count starts with 1 or 2 we ceil to the nearest order of the order of magnitude -1
     * else we ceil to the nearest order of the order of magnitude -2
     * e.g: 1234 -> 1300, 1201 -> 1300
     */
    public Long modifyCount(Long count, int min) {
        if (count == null) {
            return 0L;
        }

        // If count < min, the count is too small, we just return 0
        // This is a hard limit, the permission system allows to set per cohort
        // minimum counts but we anyways check here to make absolutely sure we
        // never go under min
        if (count < min) {
            return 0L;
        }

        // Otherwise we round depending on the order of magnitude of count:
        // for any 100s value round to the 10s, for any 1000s value to the 100s, ...

        // We get the order of magnitude of the count -1
        // as we want to round not to the order of magnitude but to one magnitude lower
        double x = Math.floor(Math.log10(count)) - 1; // faster than String.valueOf(count).length()-1

        // We divide by this divisor, then use ceil, then multiply it again
        // this way we can use the ceil function to round to the nearest 10^(x-1)
        double xDivisor = Math.pow(10, x);
        double tempCount = count / xDivisor;
        tempCount = Math.ceil(tempCount);
        Long result = (long) (tempCount * xDivisor);
        Log.infof("Modified count: %d -> %d", count, result);
        return result;
    }

    private int queryMinCount() {
        if (config == null || config.privacy() == null || config.privacy().query() == null) {
            return 100;
        }
        return Math.max(0, config.privacy().query().minCount());
    }
}

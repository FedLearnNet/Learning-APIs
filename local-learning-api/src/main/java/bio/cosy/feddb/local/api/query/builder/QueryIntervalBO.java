package bio.cosy.feddb.local.api.query.builder;

import bio.cosy.feddb.core.api.query.item.IntervalPartDTO;
import bio.cosy.feddb.core.api.query.item.IntervalUnit;
import bio.cosy.feddb.local.api.query.QueryRejectedException;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class QueryIntervalBO {

    // keeps event_time +/- interval inside the PostgreSQL timestamp range,
    // an overflow would only fail on matching rows and reveal them
    private static final int MAX_YEARS = 200;
    private static final long MAX_MINUTES = MAX_YEARS * IntervalUnit.YEARS.getMaxMinutes();

    // SQL interval text such as "2 weeks 3 days"
    public String intervalSql(List<IntervalPartDTO> parts, String field) {
        if (parts == null || parts.isEmpty()) {
            throw new QueryRejectedException(field + " is missing");
        }
        List<String> texts = new ArrayList<>();
        long totalMinutes = 0;
        for (IntervalPartDTO part : parts) {
            if (part == null || part.getCount() == null || part.getUnit() == null) {
                throw new QueryRejectedException(field + " needs a count and a unit");
            }
            if (part.getCount() <= 0) {
                throw new QueryRejectedException(field + " must be positive");
            }
            totalMinutes += (long) part.getCount() * part.getUnit().getMaxMinutes();
            if (totalMinutes > MAX_MINUTES) {
                throw new QueryRejectedException(field + " exceeds the maximum of " + MAX_YEARS + " years");
            }
            texts.add(part.getCount() + " " + part.getUnit().getSqlUnit());
        }
        return String.join(" ", texts);
    }
}

package bio.cosy.feddb.local.api.importer.functions.managed.builtin;

import bio.cosy.feddb.local.api.importer.functions.managed.AbstractManagedCellFunction;
import bio.cosy.feddb.local.api.importer.functions.managed.CellFunctionExecutionContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convert a temporal duration into an integer number of days, reversing a site
 * that encodes length of stay as an ISO-8601 duration ("P3D", "PT72H") or a
 * "{n} days" / "{n} hours" phrase instead of a plain day count.
 * <p>
 * Already-numeric or unrecognised values pass through unchanged.
 */
@ApplicationScoped
public class DurationToDaysFunction extends AbstractManagedCellFunction {

    private static final Pattern ISO_DAYS = Pattern.compile("P(\\d+)D");
    private static final Pattern ISO_HOURS = Pattern.compile("PT(\\d+)H");
    private static final Pattern PHRASE = Pattern.compile("(?i)(\\d+)\\s*(day|days|d|hour|hours|h)");

    @Override
    public String methodName() {
        return "Duration To Days";
    }

    @Override
    public String description() {
        return "Convert an ISO-8601 duration (P3D / PT72H) or '{n} days|hours' phrase into an integer number of days.";
    }

    @Override
    public Object execute(CellFunctionExecutionContext context) {
        Object value = context.getValue();
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return value;
        }

        Matcher days = ISO_DAYS.matcher(text);
        if (days.matches()) {
            return Long.parseLong(days.group(1));
        }
        Matcher hours = ISO_HOURS.matcher(text);
        if (hours.matches()) {
            return Long.parseLong(hours.group(1)) / 24L;
        }
        Matcher phrase = PHRASE.matcher(text);
        if (phrase.matches()) {
            long amount = Long.parseLong(phrase.group(1));
            String unit = phrase.group(2).toLowerCase();
            return unit.startsWith("h") ? amount / 24L : amount;
        }
        return value; // not a recognised duration — leave untouched
    }
}

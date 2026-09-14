package de.unihamburg.daibetes.base;

import java.util.HashMap;
import java.util.Map;

public final class CypherTemplate {

    private final String template;
    private final Map<String, String> bindings = new HashMap<>();

    private CypherTemplate(String template) {
        this.template = template;
    }

    public static CypherTemplate of(String template) {
        return new CypherTemplate(template);
    }

    public CypherTemplate bind(String key, String value) {
        bindings.put(key, value);
        return this;
    }

    public String build() {
        String result = template;

        for (var entry : bindings.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            result = result.replace(placeholder, entry.getValue());
        }

        return result;
    }
}

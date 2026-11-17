package bio.cosy.feddb.local.api.importer.transformer;

import java.util.Locale;

public class ConnectorTransformerStatics {
    public static final String TOOLS_MODULE = "tools";
    public static final String CODE_MODULE = "Custom code";

    // Compares names ignoring case, spaces and underscores
    public static String plain(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

}

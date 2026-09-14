package de.unihamburg.daibetes.agent.anlysis;

public enum RequestRoute {
    FILES,
    TOOLS,
    HELP,
    TOOL_RECOMMEND,
    TOOL_DIRECT_SELECT,
    TOOL_DATA_FIT,
    UNKNOWN;

    public static RequestRoute from(String s) {
        if (s == null) return UNKNOWN;
        try {
            return RequestRoute.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}

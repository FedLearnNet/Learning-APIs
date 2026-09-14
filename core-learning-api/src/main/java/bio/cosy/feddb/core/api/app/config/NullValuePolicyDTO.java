package bio.cosy.feddb.core.api.app.config;

import lombok.Data;

import java.util.List;

@Data
public class NullValuePolicyDTO {
    // truly missing CSV fields (empty between delimiters), e.g. `a,,c`
    private Boolean prohibitedEmptyCell;

    //prohibited empty string values after parsing, e.g. "" (quoted empty)
    private Boolean prohibitedEmptyString;

    // prohibited whitespace-only strings like "   " (before/after trimming — define in backend
    private Boolean prohibitedWhitespaceString;

    // prohibited string tokens that represent null (case-insensitive), e.g. "null", "none", "na"
    private Boolean prohibitedNullLiterals;

    // prohibited numeric NaN (actual NaN after parsing numeric columns)
    private Boolean prohibitedNaN;

    // prohibited 0 / 0.0 as a "null sentinel" (domain-specific)
    private Boolean prohibitedZeroAsNull;

    // Explicit list of null tokens
    // Used if prohibitedNullLiterals === true
    private List<String> nullLiterals;
}

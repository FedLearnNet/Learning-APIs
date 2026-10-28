package bio.cosy.feddb.core.api.info;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class BaseStartupImportsBO {

    private volatile StartupImportState state = StartupImportState.IMPORTING;

    // state reported on /info
    public StartupImportState loadState() {
        return state;
    }
}

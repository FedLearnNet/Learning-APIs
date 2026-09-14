package bio.cosy.feddb.core.base;

import jakarta.validation.groups.Default;

public interface ValidationGroups {
    interface Post extends Default {
    }
    interface Put extends Default {
    }
    interface Patch extends Default {
    }
    interface Delete extends Default {
    }
    /**
     * Marker validation group for upsert operations (create or update)
     * Allows payloads that mix new items (no ID yet) and existing items (with ID)
     */
    interface Upsert extends Default {
    }
}

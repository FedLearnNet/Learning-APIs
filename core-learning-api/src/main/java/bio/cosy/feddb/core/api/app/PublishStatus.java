package bio.cosy.feddb.core.api.app;

public enum PublishStatus {
    PUBLISHED("published"),
    UNPUBLISHED("unpublished");

    private final String name;

    PublishStatus(String s) {
        name = s;
    }

    public boolean equalsName(String otherName) {
        return name.equalsIgnoreCase(otherName);
    }

    public boolean equalsName(PublishStatus status) {
        return equalsName(status.name());
    }

    public String toString() {
        return this.name;
    }

    public static PublishStatus valueOfLabel(String label) {
        for (PublishStatus e : values()) {
            if (e.name.equalsIgnoreCase(label)) {
                return e;
            }
        }
        return null;
    }
}

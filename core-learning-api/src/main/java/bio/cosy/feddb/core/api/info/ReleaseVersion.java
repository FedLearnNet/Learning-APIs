package bio.cosy.feddb.core.api.info;

import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Getter
@EqualsAndHashCode
public class ReleaseVersion implements Comparable<ReleaseVersion> {

    private static final Pattern FORMAT = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)(?:\\.(dev|rc(\\d+)))?");

    private static final Comparator<ReleaseVersion> ORDER = Comparator.comparingInt(ReleaseVersion::getMajor)
            .thenComparingInt(ReleaseVersion::getMinor)
            .thenComparingInt(ReleaseVersion::getPatch)
            .thenComparing(ReleaseVersion::getStage)
            .thenComparingInt(ReleaseVersion::getRc);

    public enum Stage {DEV, RC, RELEASE}

    private final int major;
    private final int minor;
    private final int patch;
    private final Stage stage;
    private final int rc;

    private ReleaseVersion(int major, int minor, int patch, Stage stage, int rc) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.stage = stage;
        this.rc = rc;
    }

    public static Optional<ReleaseVersion> parse(String version) {
        if (version == null) {
            return Optional.empty();
        }
        Matcher matcher = FORMAT.matcher(version.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        Stage stage = matcher.group(4) == null ? Stage.RELEASE : matcher.group(4).equals("dev") ? Stage.DEV : Stage.RC;
        return Optional.of(new ReleaseVersion(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)),
                stage,
                matcher.group(5) == null ? 0 : Integer.parseInt(matcher.group(5))));
    }

    public static boolean isNewer(String candidate, String current) {
        Optional<ReleaseVersion> a = parse(candidate);
        Optional<ReleaseVersion> b = parse(current);
        return a.isPresent() && b.isPresent() && a.get().compareTo(b.get()) > 0;
    }

    @Override
    public int compareTo(ReleaseVersion other) {
        return ORDER.compare(this, other);
    }
}

package bio.cosy.feddb.local.helper;

import io.quarkus.runtime.LaunchMode;


public class QuarkusModeService {

    public static boolean isDevMode() {
        return LaunchMode.current() == LaunchMode.DEVELOPMENT;
    }

}

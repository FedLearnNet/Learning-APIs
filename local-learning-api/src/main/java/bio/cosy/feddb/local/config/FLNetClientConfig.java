package bio.cosy.feddb.local.config;

import bio.cosy.feddb.local.api.cohort.permission.AutoMetricsAccess;
import bio.cosy.feddb.local.api.cohort.permission.AutoStatisticsAccess;
import bio.cosy.feddb.local.api.cohort.permission.AutoTrainingAccess;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import io.smallrye.config.WithParentName;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@ConfigMapping(prefix = "flnet")
public interface FLNetClientConfig {

    GlobalConfig global();

    PrivacyConfig privacy();

    RequestConfig request();

    UserConfig user();

    @WithName("auto-subscribe")
    AutoSubscribe autoSubscribe();

    CalendarConfig calendar();

    ConnectorConfig connector();

    CohortConfig cohort();

    interface CohortConfig {

        @WithName("delete-traceability")
        @WithDefault("true")
        boolean deleteTraceability();

        @WithName("delete-batch-size")
        @WithDefault("100")
        int deleteBatchSize();

        @WithName("cohort-permission")
        DefaultCohortPermission defaultCohortPermission();

        @WithName("deactivate-automatic-cohort-permission")
        DeactivateAutomaticCohortPermission deactivateAutomaticCohortPermission();
    }

    interface ConnectorConfig {

        @WithDefault("Unique Patient ID")
        String externalIdColumn();

        @WithDefault("50")
        int importProgressPublishBatchSize();

        @WithDefault("50000")
        int etlSortChunkSize();

        @WithName("etl-parallelism")
        @WithDefault("10")
        int etlParallelism();

        @WithDefault("10")
        int previewRows();

        // importer file rows kept in memory before spilling to an NDJSON disk cache; 0 = disk first
        @WithDefault("0")
        int importRowLimitForDiskCache();

        // tables of a multi-table upload read concurrently; capped by the available cores
        @WithName("import-parse-parallelism")
        @WithDefault("4")
        int importParseParallelism();

        // threads profiling the columns of one table; shared out between concurrently read tables
        @WithName("import-profile-parallelism")
        @WithDefault("4")
        int importProfileParallelism();

        // base dir for ETL temp files; unset = JVM temp dir
        @WithName("etl-work-directory")
        Optional<String> etlWorkDirectory();

    }


    interface DeactivateAutomaticCohortPermission {

        @WithDefault("false")
        boolean statistics();

        @WithDefault("false")
        boolean metrics();

        @WithDefault("false")
        boolean learning();
    }

    interface DefaultCohortPermission {

        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        @WithName("query-retry-time")
        @WithDefault("3")
        Integer queryRetryTime();

        @WithName("is-allowed-to-query")
        @WithDefault("true")
        Boolean isAllowedToQuery();

        @WithName("query-sample-threshold")
        @WithDefault("100")
        Integer querySampleThreshold();

        @WithName("global-user-id")
        Optional<String> globalUserId();

        @WithName("auto-training-access")
        @WithDefault("ALL")
        AutoTrainingAccess autoTrainingAccess();

        @WithName("auto-statistics-access")
        @WithDefault("ALL")
        AutoStatisticsAccess autoStatisticsAccess();

        @WithName("auto-metrics-access")
        @WithDefault("ALL")
        AutoMetricsAccess autoMetricsAccess();
    }

    interface AutoSubscribe {
        @WithParentName
        Map<String, AutoSubscribeGroupConfig> groups();

        @WithName("enabled")
        @WithDefault("false")
        Boolean enabled();
    }

    interface AutoSubscribeGroupConfig {

        @WithName("global-schema-id")
        String globalSchemaId();

        @WithName("cohort-name")
        String cohortName();

        @WithName("etl-data-path")
        Optional<String> etlDataPath();

        @WithName("connector-path")
        Optional<String> connectorPath();

        @WithName("add-for-user")
        Optional<String> addForUser();

        @WithName("use-default-cohort-permission")
        @WithDefault("true")
        Boolean useDefaultCohortPermission();

        @WithName("cohort-permission")
        DefaultCohortPermission defaultCohortPermission();

        @WithName("enabled-file-watching")
        @WithDefault("false")
        Boolean fileWatching();
    }


    interface PrivacyConfig {

        QueryPrivacyConfig query();

        StatisticsPrivacyConfig statistics();
    }

    interface QueryPrivacyConfig {

        @WithName("min-count")
        @WithDefault("100")
        int minCount();
    }

    interface StatisticsPrivacyConfig {

        @WithDefault("true")
        boolean enabled();

        @WithName("min-subjects")
        @WithDefault("10")
        int minSubjects();

        @WithName("count-rounding-base")
        @WithDefault("5")
        int countRoundingBase();

        @WithName("unique-rounding-base")
        @WithDefault("5")
        int uniqueRoundingBase();

        @WithName("min-category-count")
        @WithDefault("3")
        int minCategoryCount();

        @WithName("numeric-decimals")
        @WithDefault("1")
        int numericDecimals();

        @WithName("remove-extreme-values")
        @WithDefault("true")
        boolean removeExtremeValues();

        @WithName("keep-local-ids")
        @WithDefault("false")
        boolean keepLocalIds();
    }


    interface RequestConfig {

        DataStatisticsConfig dataStatistics();

        RunMetricsConfig runMetrics();
    }

    interface DataStatisticsConfig {

        @WithDefault("true")
        boolean enabled();

        @WithDefault("false")
        boolean returnAllOnEmpty();
    }

    interface RunMetricsConfig {

        @WithDefault("true")
        boolean enabled();
    }

    interface UserConfig {

        @WithDefault("SYSTEM")
        String systemUserName();
    }

    interface CalendarConfig {

        GregorianConfig gregorian();
    }

    interface GregorianConfig {

        @WithDefault("pragmatic")
        String adoption();
    }

    interface GlobalConfig {

        SocketConfig socket();

        AuthConfig auth();
    }

    interface AuthConfig {

        @WithName("enable")
        @WithDefault("true")
        boolean enabled();

        Optional<String> username();

        Optional<String> password();

        @WithName("authorization-header")
        Optional<String> authorizationHeader();
    }

    interface SocketConfig {

        @WithName("enabled")
        @WithDefault("true")
        Boolean enabled();

        URI uri();

        ReconnectConfig reconnect();
    }

    interface ReconnectConfig {

        @WithDefault("5")
        int attempts();

        DelayConfig delay();
    }

    interface DelayConfig {

        @WithDefault("1000")
        int init();

        @WithDefault("30000")
        int reconnect();
    }
}

package rest.helper;

import bio.cosy.feddb.local.api.cohort.permission.AutoMetricsAccess;
import bio.cosy.feddb.local.api.cohort.permission.AutoStatisticsAccess;
import bio.cosy.feddb.local.api.cohort.permission.AutoTrainingAccess;
import bio.cosy.feddb.local.config.FLNetClientConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Optional;

public final class AutoSubscribeTestHelper {

    private AutoSubscribeTestHelper() {
    }

    public static File createTempCsv(String prefix) throws IOException {
        File csv = Files.createTempFile(prefix, ".csv").toFile();
        try (FileWriter w = new FileWriter(csv)) {
            w.write("Unique Patient ID,13,14\n");
            w.write("AUTO_SUB_PATIENT_1,11,1.5\n");
            w.write("AUTO_SUB_PATIENT_2,22,2.5\n");
        }
        csv.deleteOnExit();
        return csv;
    }

    public static void appendRowToCsv(File csv, String externalId, int intValue, double floatValue)
            throws IOException {
        try (FileWriter w = new FileWriter(csv, true)) {
            w.write(externalId + "," + intValue + "," + floatValue + "\n");
        }
    }

    public static FLNetClientConfig.AutoSubscribeGroupConfig buildGroupConfig(
            String schemaName,
            Optional<String> connectorPath,
            Optional<String> etlDataPath,
            boolean fileWatching) {
        return new FLNetClientConfig.AutoSubscribeGroupConfig() {
            @Override
            public String globalSchemaId() {
                return "00000000-0000-0000-0000-000000000000";
            }

            @Override
            public String cohortName() {
                return schemaName;
            }

            @Override
            public Optional<String> etlDataPath() {
                return etlDataPath;
            }

            @Override
            public Optional<String> connectorPath() {
                return connectorPath;
            }


            @Override
            public Optional<String> addForUser() {
                return Optional.of("SYSTEM");
            }

            @Override
            public Boolean useDefaultCohortPermission() {
                return false;
            }

            @Override
            public FLNetClientConfig.DefaultCohortPermission defaultCohortPermission() {
                return new FLNetClientConfig.DefaultCohortPermission() {
                    @Override
                    public boolean enabled() {
                        return false;
                    }

                    @Override
                    public Integer queryRetryTime() {
                        return 3;
                    }

                    @Override
                    public Boolean isAllowedToQuery() {
                        return Boolean.TRUE;
                    }

                    @Override
                    public Integer querySampleThreshold() {
                        return 100;
                    }
                    @Override
                    public Optional<String> globalUserId() {
                        return Optional.empty();
                    }

                    @Override
                    public AutoTrainingAccess autoTrainingAccess() {
                        return AutoTrainingAccess.ALL;
                    }

                    @Override
                    public AutoStatisticsAccess autoStatisticsAccess() {
                        return AutoStatisticsAccess.ALL;
                    }

                    @Override
                    public AutoMetricsAccess autoMetricsAccess() {
                        return AutoMetricsAccess.ALL;
                    }
                };
            }



            @Override
            public Boolean fileWatching() {
                return fileWatching;
            }
        };
    }
}

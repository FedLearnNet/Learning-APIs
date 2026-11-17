package eval.agent.etl.transform.setting;

import bio.cosy.feddb.local.config.FLNetClientConfig;
import lombok.AllArgsConstructor;

import java.util.Optional;

@AllArgsConstructor
public class TransformerWriterSubscription implements FLNetClientConfig.AutoSubscribeGroupConfig {

    private final TransformerWriterDataset dataset;

    @Override
    public String globalSchemaId() {
        return dataset.getSchemaId();
    }

    @Override
    public String cohortName() {
        return dataset.getName();
    }

    @Override
    public Optional<String> etlDataPath() {
        return Optional.of(dataset.getDataPath());
    }

    @Override
    public Optional<String> connectorPath() {
        return Optional.empty();
    }

    @Override
    public Optional<String> addForUser() {
        return Optional.empty();
    }

    @Override
    public Boolean useDefaultCohortPermission() {
        return true;
    }

    @Override
    public FLNetClientConfig.DefaultCohortPermission defaultCohortPermission() {
        return null;
    }

    @Override
    public Boolean fileWatching() {
        return false;
    }
}

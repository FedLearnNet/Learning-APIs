package eval.agent.etl.transform.pipeline;

import bio.cosy.feddb.core.api.file.FileParsingSettingsDTO;
import bio.cosy.feddb.core.helper.ResourceLoader;
import bio.cosy.feddb.local.api.cohort.AutoSubscriberBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorBO;
import bio.cosy.feddb.local.api.importer.connector.ConnectorDTO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.files.upload.ConnectorFileUploadBO;
import bio.cosy.feddb.local.config.FLNetClientConfig;
import eval.agent.etl.transform.setting.TransformerWriterDataset;
import eval.agent.etl.transform.setting.TransformerWriterSubscription;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;

@ApplicationScoped
public class TransformerWriterUploader {

    @Inject
    AutoSubscriberBO autoSubscriberBO;

    @Inject
    ConnectorFileUploadBO uploadBO;

    @Inject
    ConnectorBO connectorBO;

    @Inject
    FLNetClientConfig config;

    @Transactional
    @TransactionConfiguration(timeout = 3600)
    public ConnectorDTO upload(TransformerWriterDataset dataset) throws IOException {
        String keycloakId = config.user().systemUserName();
        Long cohortId = autoSubscriberBO.ensureCohort(dataset.getName(), new TransformerWriterSubscription(dataset), keycloakId);

        File export = ResourceLoader.loadAsFile(dataset.getDataPath());
        FileParsingSettingsDTO parsing = FileParsingSettingsDTO.forFileName(export.getName());
        parsing.setDelimiter(dataset.getDelimiter());
        ConnectorFilesDTO file = uploadBO.uploadFileForCohort(cohortId, export,
                Path.of(dataset.getDataPath()).getFileName().toString(), keycloakId, parsing);

        FileUploadSettingsDTO input = createFileUploadSetting(file, parsing);
        ConnectorDTO connector = createConnector(cohortId, dataset, input);
        connector = connectorBO.create(connector, false, false);
        Log.infof("[ETL-Eval:upload] Uploaded %s into cohort %d, connector %d", dataset.getName(), cohortId, connector.getId());
        return connector;
    }

    private FileUploadSettingsDTO createFileUploadSetting(ConnectorFilesDTO file, FileParsingSettingsDTO parsing) {
        FileUploadSettingsDTO input = new FileUploadSettingsDTO();
        input.setMode("FILE");
        input.setFileId(file.getId());
        input.setDelimiter(parsing.getDelimiter());
        input.setHasHeader(parsing.isHasHeader());
        input.setFirstSheetOnly(parsing.getFirstSheetOnly());
        return input;
    }

    private ConnectorDTO createConnector(Long cohortId, TransformerWriterDataset dataset, FileUploadSettingsDTO input) {
        ConnectorDTO connector = new ConnectorDTO();
        connector.setName(dataset.getName());
        connector.setCohortId(cohortId);
        connector.setInputConfig(input);
        connector.setMergeConfig(dataset.getMerge());
        connector.setTransformer(new ArrayList<>());
        connector.setSchemaMapping(new ArrayList<>());
        return connector;
    }
}

package bio.cosy.feddb.local.api.importer.files.upload;

import bio.cosy.feddb.local.api.importer.files.ConnectorFileUploadRequestDTO;
import bio.cosy.feddb.local.api.importer.files.progress.ImportProgressTracker;
import bio.cosy.feddb.local.api.importer.files.reupload.ConnectorFileReuploadBO;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;

/**
 * Runs one import, wherever it is called from.
 *
 * <p>The two upload endpoints differ only in how they answer: one waits for the result, the other
 * streams what happens on the way to it. The work itself is the same, and the streaming one runs it
 * on a worker thread - so it lives here, in a bean of its own, with its transaction boundary in one
 * place.</p>
 */
@ApplicationScoped
public class ConnectorFileImportRunner {

    private static final int ANALYSIS_TIMEOUT_SECONDS = 15 * 60;

    @Inject
    ConnectorFileUploadBO uploadBO;

    @Inject
    ConnectorFileReuploadBO reuploadBO;

    @Inject
    ImportProgressTracker progress;

    /**
     * Imports the file and reports how it ended.
     *
     * <p>The report goes out only once the import's transaction has committed. Whoever hears it acts
     * on it straight away: the connector editor saves a connector whose input file was just replaced.
     * Told any earlier, that save read the connector before the replacement committed and then failed
     * to lock it, because the replacement had moved the connector's version on in the meantime.</p>
     *
     * @param keycloakId the caller, passed in rather than looked up: on the streaming path this runs
     *                   on a worker thread, where there is no caller to ask
     */
    @ActivateRequestContext
    public ConnectorFileImportResultDTO run(
            Long cohortId,
            ConnectorFileUploadRequestDTO request,
            Long connectorId,
            String importId,
            String keycloakId
    ) {
        ConnectorFileImportResultDTO result = importInTransaction(cohortId, request, connectorId, importId, keycloakId);
        progress.finished(importId, result);
        return result;
    }

    /**
     * The import's database work, committed when this returns. Called on this bean itself, which
     * Quarkus still intercepts, so the transaction applies; it must stay non-private for that.
     */
    @Transactional
    @TransactionConfiguration(timeout = ANALYSIS_TIMEOUT_SECONDS)
    ConnectorFileImportResultDTO importInTransaction(
            Long cohortId,
            ConnectorFileUploadRequestDTO request,
            Long connectorId,
            String importId,
            String keycloakId
    ) {
        return doImport(cohortId, request, connectorId, importId, keycloakId);
    }

    /**
     * The same import, run away from the request that asked for it.
     *
     * <p>Nothing here may throw: there is no caller left to catch it, so the failure goes to whoever
     * is watching the import. The files are released at the end because this import owns them - they
     * were taken out of the request precisely so that it ending would not take them away.</p>
     */
    public void runDetached(
            Long cohortId,
            ConnectorFileUploadRequestDTO request,
            Long connectorId,
            String importId,
            String keycloakId
    ) {
        try {
            run(cohortId, request, connectorId, importId, keycloakId);
        } catch (RuntimeException e) {
            Log.warnf(e, "Import %s failed", importId);
            progress.failed(importId, e.getMessage());
        } finally {
            request.getFiles().forEach(DetachedUpload::release);
        }
    }

    private ConnectorFileImportResultDTO doImport(
            Long cohortId,
            ConnectorFileUploadRequestDTO request,
            Long connectorId,
            String importId,
            String keycloakId
    ) {
        if (connectorId == null) {
            return ConnectorFileImportResultDTO.stored(
                    uploadBO.uploadFileForCohort(cohortId, request, keycloakId, importId));
        }
        if (request.getFiles().size() != 1) {
            throw new BadRequestException(
                    "A connector reads a single input file, so a replacement is a single file");
        }
        return reuploadBO.replaceConnectorInput(cohortId, connectorId, request, keycloakId, importId);
    }
}

package bio.cosy.feddb.local.api.cohort;

import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.tre.TreSourceDTO;
import bio.cosy.feddb.local.api.eam.ClientManager;
import bio.cosy.feddb.local.services.GlobalAPIFileService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.client.api.ClientMultipartForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TreSourceBO {

    private static final long MAX_TEST_DATA_FILE = 100L * 1024 * 1024;

    @Inject
    CohortAO cohortAO;

    @Inject
    CohortMapper cohortMapper;

    @Inject
    ClientManager clientManager;

    @Inject
    @RestClient
    GlobalAPIFileService globalFiles;

    public FileDTO replaceTestDataFile(Long cohortId, FileUpload upload) {
        if (upload != null && upload.size() > MAX_TEST_DATA_FILE) {
            throw new BadRequestException("Test data files are limited to 100 MB");
        }
        FileDTO uploaded = null;
        if (upload != null) {
            ClientMultipartForm form = ClientMultipartForm.create();
            form.binaryFileUpload("file", upload.fileName(), upload.uploadedFile().toString(), "application/octet-stream");
            uploaded = globalFiles.upload(form);
        }
        swapTestDataFileTransactional(cohortId, uploaded).ifPresent(previous -> {
            try (Response ignored = globalFiles.delete(previous.getId(), previous.getSecret())) {
                Log.debugf("Removed test data file %d from the platform", previous.getId());
            } catch (RuntimeException e) {
                Log.warnf(e, "Could not remove test data file %d from the platform", previous.getId());
            }
        });
        clientManager.sendTreSources();
        return uploaded;
    }

    @Transactional
    public Optional<FileDTO> swapTestDataFileTransactional(Long cohortId, FileDTO file) {
        CohortEntity cohort = cohortAO.findByIdOptional(cohortId).orElseThrow(NotFoundException::new);
        ExternalDatabaseDTO database = cohort.getExternalDatabase();
        if (database == null) {
            throw new BadRequestException("Cohort " + cohortId + " links no external database");
        }
        cohort.setExternalDatabase(database.toBuilder().testDataFile(file).build());
        return Optional.ofNullable(database.getTestDataFile());
    }

    @Transactional
    public List<TreSourceDTO> listTransactional() {
        return cohortAO.listExternal().stream().map(cohortMapper::entityToTreSource).toList();
    }

    @Transactional
    public Optional<Long> findCohortIdTransactional(String treSourceId) {
        return cohortAO.findByTreSourceId(treSourceId).map(CohortEntity::getId);
    }

    @Transactional
    public Optional<ExternalDatabaseDTO> findDatabaseTransactional(String treSourceId) {
        return cohortAO.findByTreSourceId(treSourceId).map(CohortEntity::getExternalDatabase);
    }
}

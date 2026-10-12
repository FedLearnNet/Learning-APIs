package bio.cosy.feddb.local.api.harmonization;

import bio.cosy.feddb.core.api.file.FileResult;
import bio.cosy.feddb.local.agent.etl.transform.assistant.TransformerAssistantMessageDTO;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDetailDTO;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.runtime.TransactionConfiguration;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class HarmonizationServiceImpl implements HarmonizationService {

    @Inject
    HarmonizationBO bo;

    @Inject
    HarmonizationRunBO runBO;

    @Inject
    HarmonizationFileBO fileBO;

    @Inject
    HarmonizationAssistantBO assistantBO;

    @Inject
    UserIdentity userIdentity;

    @Override
    @Transactional
    public List<HarmonizationDTO> list() {
        return bo.list(userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public HarmonizationDTO get(Long id) {
        return bo.getById(id, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public HarmonizationDTO create(HarmonizationDTO dto) {
        return bo.create(dto, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public HarmonizationDTO update(Long id, HarmonizationDTO dto) {
        return bo.update(id, dto, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        bo.delete(id, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    @TransactionConfiguration(timeout = 3600)
    public List<HarmonizationTargetDTO> targets(Long fileId, String key) {
        return runBO.targets(fileId, key, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    @TransactionConfiguration(timeout = 3600)
    public HarmonizationResultDTO run(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        HarmonizationResultDTO result = runBO.run(bo.getById(id, keycloakId), keycloakId);
        bo.setOutputFile(id, result.outputFileId(), keycloakId);
        return result;
    }

    @Override
    @Transactional
    public void startAssistant(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        HarmonizationDTO harmonization = bo.getById(id, keycloakId);
        assistantBO.start(harmonization, runBO.fileProfiles(harmonization, keycloakId), keycloakId);
    }

    @Override
    public Multi<TransformerAssistantMessageDTO> assistantEvents(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        return Uni.createFrom().item(() -> QuarkusTransaction.requiringNew().call(() -> bo.getById(id, keycloakId)))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .onItem().transformToMulti(harmonization -> assistantBO.events(id));
    }

    @Override
    @Transactional
    public boolean assistantRunning(Long id) {
        bo.getById(id, userIdentity.getKeycloakId());
        return assistantBO.isRunning(id);
    }

    @Override
    @Transactional
    public void assistantMessage(Long id, TransformerAssistantMessageDTO message) {
        bo.getById(id, userIdentity.getKeycloakId());
        assistantBO.message(id, message);
    }

    @Override
    @Transactional
    @TransactionConfiguration(timeout = 3600)
    public List<Map<String, Object>> previewTransformed(int steps, HarmonizationDTO dto) {
        return runBO.previewTransformed(dto, steps, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    @TransactionConfiguration(timeout = 3600)
    public HarmonizationResultDTO previewHarmonized(HarmonizationDTO dto) {
        return runBO.previewHarmonized(dto, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public List<Map<String, Object>> sample(Long fileId, int rows) {
        return runBO.sample(fileId, rows, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public List<ConnectorFilesDetailDTO> listFiles() {
        return fileBO.list(userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public ConnectorFilesDetailDTO getFile(Long fileId) {
        return fileBO.getById(fileId, userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    @TransactionConfiguration(timeout = 3600)
    public ConnectorFilesDetailDTO uploadFile(FileUpload file) {
        return fileBO.upload(file.uploadedFile().toFile(), file.fileName(), userIdentity.getKeycloakId());
    }

    @Override
    @Transactional
    public Response downloadFile(Long fileId) {
        FileResult result = fileBO.loadFile(fileId, userIdentity.getKeycloakId());
        return Response.ok(result.file())
                .header("Content-Disposition", "attachment; filename=\"" + result.fileName() + "\"")
                .build();
    }
}

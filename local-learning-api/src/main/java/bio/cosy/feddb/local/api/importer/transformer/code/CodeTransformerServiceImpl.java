package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.pipeline.PipelineDTO;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class CodeTransformerServiceImpl implements CodeTransformerService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    CodeTransformerRunnerBO runnerBO;

    @Inject
    CodeTransformerBO codeTransformerBO;

    @Override
    public List<CodeTransformerDTO> list() {
        return codeTransformerBO.list();
    }

    @Override
    public CodeTransformerDTO save(CodeTransformerSaveDTO request) {
        return codeTransformerBO.save(request, userIdentity.getKeycloakId());
    }

    @Override
    public CodeTransformerDTO get(Long id) {
        return codeTransformerBO.getById(id);
    }

    @Override
    public void delete(Long id) {
        codeTransformerBO.delete(id);
    }

    @Override
    public CodeTransformerDTO build(Long id) {
        return codeTransformerBO.build(id);
    }

    @Override
    public Multi<PipelineDTO> buildUpdates(Long id) {
        return codeTransformerBO.buildUpdates(id);
    }

    @Override
    public CodeTransformerCheckResultDTO check(CodeTransformerCheckDTO request) {
        return runnerBO.check(request, userIdentity.getKeycloakId());
    }

    @Override
    public CodeTransformerRunnerDTO getRunner() {
        return runnerBO.status();
    }

    @Override
    public CodeTransformerRunnerDTO startRunner() {
        runnerBO.start(userIdentity.getKeycloakId());
        return runnerBO.status();
    }

    @Override
    public CodeTransformerRunnerDTO stopRunner() {
        runnerBO.stop();
        return runnerBO.status();
    }

    @Override
    public Multi<CodeTransformerLogDTO> runnerLogs() {
        return runnerBO.logs();
    }
}

package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.check.CodeTransformerCheckResultDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerContainerBO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerRequestBO;
import bio.cosy.feddb.local.api.importer.transformer.code.runner.CodeTransformerRunnerDTO;
import bio.cosy.feddb.local.api.importer.transformer.code.save.CodeTransformerSaveDTO;
import bio.cosy.feddb.local.api.pipeline.PipelineDTO;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class CodeTransformerServiceImpl implements CodeTransformerService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    CodeTransformerContainerBO containerBO;

    @Inject
    CodeTransformerRequestBO requestBO;

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

    // On a worker thread: the stream starts with the latest build, read from the database
    @Override
    @Blocking
    public Multi<PipelineDTO> buildUpdates(Long id) {
        return codeTransformerBO.buildUpdates(id);
    }

    @Override
    public CodeTransformerCheckResultDTO check(CodeTransformerCheckDTO request) {
        return requestBO.check(request, userIdentity.getKeycloakId());
    }

    @Override
    public CodeTransformerRunnerDTO getRunner() {
        return containerBO.status();
    }

    @Override
    public CodeTransformerRunnerDTO startRunner() {
        containerBO.start(userIdentity.getKeycloakId());
        return containerBO.status();
    }

    @Override
    public CodeTransformerRunnerDTO stopRunner() {
        containerBO.stop();
        return containerBO.status();
    }

    @Override
    public Multi<CodeTransformerLogDTO> runnerLogs() {
        return containerBO.logs();
    }
}

package bio.cosy.feddb.local.api.importer.transformer.code;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CodeTransformerServiceImpl implements CodeTransformerService {

    @Inject
    UserIdentity userIdentity;

    @Inject
    CodeTransformerBO codeTransformerBO;

    @Override
    public CodeTransformerCheckResultDTO check(CodeTransformerCheckDTO request) {
        return codeTransformerBO.check(request, userIdentity.getKeycloakId());
    }

    @Override
    public CodeTransformerRunnerDTO getRunner() {
        return codeTransformerBO.status();
    }

    @Override
    public CodeTransformerRunnerDTO startRunner() {
        codeTransformerBO.start(userIdentity.getKeycloakId());
        return codeTransformerBO.status();
    }

    @Override
    public CodeTransformerRunnerDTO stopRunner() {
        codeTransformerBO.stop();
        return codeTransformerBO.status();
    }

    @Override
    public Multi<CodeTransformerLogDTO> runnerLogs() {
        return codeTransformerBO.logs();
    }
}

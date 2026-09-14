package bio.cosy.feddb.local.api.importer.functions;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class FunctionServiceImpl implements FunctionService {

    @Inject
    FunctionManagementBO functionManagementBO;

    @Override
    public FunctionsDTO list() {
        return functionManagementBO.getAll();
    }

    @Override
    public List<FunctionsDetailDTO> getAllDetail() {
        return functionManagementBO.getAllDetail();
    }

    @Override
    public FunctionsDetailDTO retrieve(String methodName, String moduleName) {
        return functionManagementBO.get(moduleName, methodName);
    }
}

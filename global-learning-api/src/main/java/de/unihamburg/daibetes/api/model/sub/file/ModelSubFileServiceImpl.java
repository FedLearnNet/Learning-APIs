package de.unihamburg.daibetes.api.model.sub.file;

import bio.cosy.feddb.core.api.file.FileResult;
import de.unihamburg.daibetes.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;

import java.io.File;

@ApplicationScoped
public class ModelSubFileServiceImpl implements ModelSubFileService {

    @Inject
    ModelSubFileBO bo;

    @Inject
    UserIdentity userIdentity;

    @Override
    @Transactional
    public Response downloadFile(Long id) {
        String keycloakId = userIdentity.getKeycloakId();

        FileResult fileResult = bo.loadFile(id, keycloakId);
        File file = fileResult.file();
        if (file.exists()) {
            return Response.ok(file)
                    .header("Content-Disposition", "attachment; filename=\"" + fileResult.fileName() + "\"")
                    .build();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("File not found: " + id)
                    .build();
        }
    }

    @Override
    @Transactional
    public Response deleteFile(Long id) {
        String keycloakId = userIdentity.getKeycloakId();
        bo.deleteById(id);
        return Response.ok().build();
    }
}

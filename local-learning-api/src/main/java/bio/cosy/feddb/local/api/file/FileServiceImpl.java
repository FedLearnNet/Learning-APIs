package bio.cosy.feddb.local.api.file;

import bio.cosy.feddb.core.api.file.FileContentDTO;
import bio.cosy.feddb.core.api.file.FileDTO;
import bio.cosy.feddb.core.api.file.FileRenameDTO;
import bio.cosy.feddb.core.api.file.FileResult;
import bio.cosy.feddb.core.api.file.analytics.FileProfile;
import bio.cosy.feddb.local.api.auth.UserIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.core.Response;
import org.apache.commons.lang3.StringUtils;
import org.jboss.resteasy.reactive.multipart.FileUpload;

import java.io.File;
import java.util.List;

@ApplicationScoped
public class FileServiceImpl implements FileService {
    //All need Transactional because of renew Secret in all get method

    @Inject
    FileBO bo;

    @Inject
    UserIdentity userIdentity;

    @Override
    @Transactional
    public List<FileDTO> listFiles() {
        String keycloakId = userIdentity.getKeycloakId();
        return bo.list(keycloakId);
    }

    @Override
    @Transactional
    public Response upload(FileUpload file) {
        String keycloakId = userIdentity.getKeycloakId();
        FileDTO created = bo.create(new FileDTO(), keycloakId, file);
        return Response.status(Response.Status.CREATED)
                .entity(created)
                .build();
    }

    @Override
    @Transactional
    public Response deleteFile(Long id, String secret) {
        String keycloakId = userIdentity.getKeycloakId();
        if (StringUtils.isNotEmpty(secret)) {
            bo.deleteByIdAndSecret(id, keycloakId);
        } else {
            bo.deleteById(id, keycloakId);
        }
        return Response.ok().build();
    }

    @Override
    @Transactional
    public FileDTO renameFile(Long id, FileRenameDTO fileRenameDTO, String secret) {
        String keycloakId = userIdentity.getKeycloakId();
        if (StringUtils.isNotEmpty(secret)) {
            return bo.renameFileBySecret(id, fileRenameDTO, secret);
        }
        return bo.renameFile(id, fileRenameDTO, keycloakId);
    }

    @Override
    @Transactional
    public FileDTO getFile(Long fileId, String secret) {
        String keycloakId = userIdentity.getKeycloakId();
        if (StringUtils.isNotEmpty(secret)) {
            return bo.getByIdAndSecret(fileId, secret);
        }
        return bo.getById(fileId, keycloakId);
    }

    @Override
    @Transactional
    public FileContentDTO getFileContent(Long id, String secret) {
        String keycloakId = userIdentity.getKeycloakId();
        if (StringUtils.isNotEmpty(secret)) {
            return bo.getFileContentBySecret(id, secret);
        }
        return bo.getFileContent(id, keycloakId);
    }

    @Override
    @Transactional
    public FileProfile getFileStatistics(Long fileId, String secret) {
        String keycloakId = userIdentity.getKeycloakId();
        if (StringUtils.isNotEmpty(secret)) {
            return bo.getFileStatisticsBySecret(fileId, secret);
        }
        return bo.getFileStatistics(fileId, keycloakId);
    }

    @Override
    @Transactional
    public Response downloadFile(Long id, String secret) {
        String keycloakId = userIdentity.getKeycloakId();

        FileResult fileResult;

        if (StringUtils.isNotEmpty(secret)) {
            fileResult = bo.loadFileSecret(id, secret);
        } else {
            fileResult = bo.loadFile(id, keycloakId);
        }

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
}

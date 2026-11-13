package bio.cosy.feddb.core.helper;

import jakarta.ws.rs.core.Response;

import java.io.File;

public class FileResponse {

    public static Response returnFile(File file, String filename, String type) {
        return Response.ok(file)
                .type("text/csv; charset=UTF-8")
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Access-Control-Expose-Headers", "Content-Disposition, Content-Length, Content-Type")
                .build();
    }

    public static Response returnCSV(File file, String filename) {
        return returnFile(file, filename, "text/csv; charset=UTF-8");
    }

    public static Response returnZip(File file, String filename) {
        return returnFile(file, filename, "application/zip");
    }
}

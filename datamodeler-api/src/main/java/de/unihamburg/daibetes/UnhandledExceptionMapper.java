package de.unihamburg.daibetes;


import bio.cosy.feddb.core.dto.ErrorResponseDTO;
import io.quarkus.logging.Log;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UnhandledExceptionMapper implements ExceptionMapper<Exception> {

    @Override
    public Response toResponse(Exception ex) {
        Log.errorf(ex, "Unhandled exception occurred: %s", ex.getMessage());
        var body = new ErrorResponseDTO(500, "Internal Server Error", ex.getMessage());
        return Response.status(500)
                .type(MediaType.APPLICATION_JSON)
                .entity(body)
                .build();
    }
}

package bio.cosy.feddb.core.dto;

public record ErrorResponseDTO(
        int status,
        String error,
        String message
) {
}

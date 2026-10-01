package bio.cosy.feddb.core.api.socket;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Answer of the global server to a {@link RelayCertSignRequestDTO}: either the signed certificate or an error.
 */
@Data
@NoArgsConstructor
public class RelayCertSignResponseDTO {
    private String globalUniqueExperimentId;
    private String uniqueRandomClinicId;
    private String currentNodeId;
    private String clientId;
    // PEM encoded certificate, null if signing was refused or failed
    private String certificate;
    private String error;

    public static RelayCertSignResponseDTO createResponse(RelayCertSignRequestDTO request, String certificate) {
        RelayCertSignResponseDTO dto = forRequest(request);
        dto.setCertificate(certificate);
        return dto;
    }

    public static RelayCertSignResponseDTO createErrorResponse(RelayCertSignRequestDTO request, String error) {
        RelayCertSignResponseDTO dto = forRequest(request);
        dto.setError(error);
        return dto;
    }

    private static RelayCertSignResponseDTO forRequest(RelayCertSignRequestDTO request) {
        RelayCertSignResponseDTO dto = new RelayCertSignResponseDTO();
        dto.setGlobalUniqueExperimentId(request.getGlobalUniqueExperimentId());
        dto.setUniqueRandomClinicId(request.getUniqueRandomClinicId());
        dto.setCurrentNodeId(request.getCurrentNodeId());
        dto.setClientId(request.getClientId());
        return dto;
    }
}

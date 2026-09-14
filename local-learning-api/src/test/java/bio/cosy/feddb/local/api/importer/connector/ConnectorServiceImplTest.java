package bio.cosy.feddb.local.api.importer.connector;

import bio.cosy.feddb.local.api.auth.UserIdentity;
import bio.cosy.feddb.local.api.cohort.member.CohortMemberAuthBO;
import bio.cosy.feddb.local.api.importer.connector.input.FileUploadSettingsDTO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesBO;
import bio.cosy.feddb.local.api.importer.files.ConnectorFilesDTO;
import bio.cosy.feddb.local.api.importer.run.preview.ConnectorPreviewBO;
import bio.cosy.feddb.local.api.importer.run.preview.PreviewResponseDTO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationBO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationRequestDTO;
import bio.cosy.feddb.local.api.importer.validation.PreviewValidationResponseDTO;
import io.smallrye.mutiny.Multi;
import jakarta.validation.Validation;
import jakarta.ws.rs.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConnectorServiceImplTest {

    @Test
    void pivotPreviewResolvesMissingCohortFromUploadedFile() {
        UserIdentity userIdentity = mock(UserIdentity.class);
        CohortMemberAuthBO cohortMemberAuthBO = mock(CohortMemberAuthBO.class);
        ConnectorFilesBO connectorFilesBO = mock(ConnectorFilesBO.class);
        ConnectorPreviewBO previewBO = mock(ConnectorPreviewBO.class);
        when(userIdentity.getKeycloakId()).thenReturn("user-1");

        ConnectorFilesDTO file = new ConnectorFilesDTO();
        file.setId(42L);
        file.setCohortId(7L);
        when(connectorFilesBO.getById(42L)).thenReturn(file);

        ConnectorConfigDTO config = new ConnectorConfigDTO();
        FileUploadSettingsDTO inputConfig = new FileUploadSettingsDTO();
        inputConfig.setFileId(42L);
        config.setInputConfig(inputConfig);
        PreviewResponseDTO expected = new PreviewResponseDTO(List.of("[]"));
        when(previewBO.previewPivot(config)).thenReturn(expected);

        ConnectorServiceImpl service = service(userIdentity, cohortMemberAuthBO, connectorFilesBO, previewBO);

        PreviewResponseDTO response = service.previewPivotTable(config);

        assertSame(expected, response);
        assertEquals(7L, config.getCohortId());
        verify(cohortMemberAuthBO).checkForEdit(7L, "user-1");
        verify(previewBO).previewPivot(config);
    }

    @Test
    void pivotPreviewRejectsMissingCohortWithoutAnUploadedFile() {
        UserIdentity userIdentity = mock(UserIdentity.class);
        CohortMemberAuthBO cohortMemberAuthBO = mock(CohortMemberAuthBO.class);
        ConnectorFilesBO connectorFilesBO = mock(ConnectorFilesBO.class);
        ConnectorPreviewBO previewBO = mock(ConnectorPreviewBO.class);
        when(userIdentity.getKeycloakId()).thenReturn("user-1");

        ConnectorServiceImpl service = service(userIdentity, cohortMemberAuthBO, connectorFilesBO, previewBO);

        assertThrows(BadRequestException.class, () -> service.previewPivotTable(new ConnectorConfigDTO()));
        verifyNoInteractions(cohortMemberAuthBO);
        verify(previewBO, never()).previewPivot(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void previewValidationChecksEditPermissionAndDelegates() {
        UserIdentity userIdentity = mock(UserIdentity.class);
        CohortMemberAuthBO cohortMemberAuthBO = mock(CohortMemberAuthBO.class);
        PreviewValidationBO previewValidationBO = mock(PreviewValidationBO.class);
        when(userIdentity.getKeycloakId()).thenReturn("user-1");

        PreviewValidationRequestDTO request = new PreviewValidationRequestDTO();
        request.setCohortId(7L);
        PreviewValidationResponseDTO expected = new PreviewValidationResponseDTO();
        when(previewValidationBO.validate(request)).thenReturn(Multi.createFrom().item(expected));

        ConnectorServiceImpl service = new ConnectorServiceImpl();
        service.userIdentity = userIdentity;
        service.cohortMemberAuthBO = cohortMemberAuthBO;
        service.previewValidationBO = previewValidationBO;

        assertSame(expected, service.validatePreview(request)
                .collect().first().await().indefinitely());
        verify(cohortMemberAuthBO).checkForEdit(7L, "user-1");
        verify(previewValidationBO).validate(request);
    }

    @Test
    void previewValidationContractRejectsMissingRequestFields() throws Exception {
        ConnectorServiceImpl service = new ConnectorServiceImpl();
        var method = ConnectorServiceImpl.class.getMethod(
                "validatePreview",
                PreviewValidationRequestDTO.class
        );

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator().forExecutables();
            var nullRequestMessages = validator.validateParameters(
                            service,
                            method,
                            new Object[]{null}
                    ).stream()
                    .map(violation -> violation.getMessage())
                    .collect(java.util.stream.Collectors.toSet());
            var missingFieldMessages = validator.validateParameters(
                            service,
                            method,
                            new Object[]{new PreviewValidationRequestDTO()}
                    ).stream()
                    .map(violation -> violation.getMessage())
                    .collect(java.util.stream.Collectors.toSet());

            assertEquals(java.util.Set.of("Preview validation request is required"), nullRequestMessages);
            assertEquals(java.util.Set.of(
                    "Cohort ID is required",
                    "Input configuration is required"
            ), missingFieldMessages);
        }
    }

    private ConnectorServiceImpl service(
            UserIdentity userIdentity,
            CohortMemberAuthBO cohortMemberAuthBO,
            ConnectorFilesBO connectorFilesBO,
            ConnectorPreviewBO previewBO
    ) {
        ConnectorServiceImpl service = new ConnectorServiceImpl();
        service.userIdentity = userIdentity;
        service.cohortMemberAuthBO = cohortMemberAuthBO;
        service.connectorFilesBO = connectorFilesBO;
        service.previewBO = previewBO;
        return service;
    }
}

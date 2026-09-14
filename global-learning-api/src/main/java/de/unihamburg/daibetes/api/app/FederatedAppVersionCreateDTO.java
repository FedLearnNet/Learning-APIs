package de.unihamburg.daibetes.api.app;


import jakarta.validation.constraints.NotBlank;

public class FederatedAppVersionCreateDTO {
    @NotBlank(message = "AppId may not be blank")
    private Long federatedAppId;

    @NotBlank(message = "UserId may not be blank")
    private Long useId;

    @NotBlank(message = "version may not be blank")
    private String version;

    @NotBlank(message = "certificationLevel may not be blank")
    private Integer certificationLevel = 0;

    public Long getFederatedAppId() {
        return federatedAppId;
    }

    public void setFederatedAppId(Long federatedAppId) {
        this.federatedAppId = federatedAppId;
    }

    public Long getUseId() {
        return useId;
    }

    public void setUseId(Long useId) {
        this.useId = useId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Integer getCertificationLevel() {
        return certificationLevel;
    }

    public void setCertificationLevel(Integer certificationLevel) {
        this.certificationLevel = certificationLevel;
    }
}


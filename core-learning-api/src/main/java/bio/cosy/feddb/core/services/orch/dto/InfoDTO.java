package bio.cosy.feddb.core.services.orch.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object (DTO) representing a Docker container from Docker API responses.
 * Maps directly to the JSON structure returned by Docker's REST API endpoints.
 */
@Data
public class InfoDTO {
    @JsonProperty("Architecture")
    private String architecture;
    @JsonProperty("Containers")
    private Integer containers;
    @JsonProperty("ContainersStopped")
    private Integer containersStopped;
    @JsonProperty("ContainersPaused")
    private Integer containersPaused;
    @JsonProperty("ContainersRunning")
    private Integer containersRunning;
    @JsonProperty("CpuCfsPeriod")
    private Boolean cpuCfsPeriod;
    @JsonProperty("CpuCfsQuota")
    private Boolean cpuCfsQuota;
    @JsonProperty("CPUShares")
    private Boolean cpuShares;
    @JsonProperty("CPUSet")
    private Boolean cpuSet;
    @JsonProperty("Debug")
    private Boolean debug;
    @JsonProperty("DiscoveryBackend")
    private String discoveryBackend;
    @JsonProperty("DockerRootDir")
    private String dockerRootDir;
    @JsonProperty("Driver")
    private String driver;
    @JsonProperty("DriverStatus")
    private List<List<String>> driverStatuses;
    @JsonProperty("SystemStatus")
    private List<Object> systemStatus;
    @JsonProperty("Plugins")
    private Map<String, List<String>> plugins;
    @JsonProperty("ExecutionDriver")
    private String executionDriver;
    @JsonProperty("LoggingDriver")
    private String loggingDriver;
    @JsonProperty("CgroupDriver")
    private String cGroupDriver;
    @JsonProperty("CgroupVersion")
    private String cGroupVersion;
    @JsonProperty("ExperimentalBuild")
    private Boolean experimentalBuild;
    @JsonProperty("HttpProxy")
    private String httpProxy;
    @JsonProperty("HttpsProxy")
    private String httpsProxy;
    @JsonProperty("ID")
    private String id;
    @JsonProperty("IPv4Forwarding")
    private Boolean ipv4Forwarding;
    @JsonProperty("BridgeNfIptables")
    private Boolean bridgeNfIptables;
    @JsonProperty("BridgeNfIp6tables")
    private Boolean bridgeNfIp6tables;
    @JsonProperty("Images")
    private Integer images;
    @JsonProperty("IndexServerAddress")
    private String indexServerAddress;
    @JsonProperty("InitPath")
    private String initPath;
    @JsonProperty("InitSha1")
    private String initSha1;
    @JsonProperty("KernelVersion")
    private String kernelVersion;
    @JsonProperty("Labels")
    private String[] labels;
    @JsonProperty("MemoryLimit")
    private Boolean memoryLimit;
    @JsonProperty("MemTotal")
    private Long memTotal;
    @JsonProperty("Name")
    private String name;
    @JsonProperty("NCPU")
    private Integer ncpu;
    @JsonProperty("NEventsListener")
    private Integer nEventsListener;
    @JsonProperty("NFd")
    private Integer nfd;
    @JsonProperty("NGoroutines")
    private Integer nGoroutines;
    @JsonProperty("NoProxy")
    private String noProxy;
    @JsonProperty("OomKillDisable")
    private Boolean oomKillDisable;
    @JsonProperty("OSType")
    private String osType;
    @JsonProperty("OomScoreAdj")
    private Integer oomScoreAdj;
    @JsonProperty("OperatingSystem")
    private String operatingSystem;
    @JsonProperty("RegistryConfig")
    private InfoRegistryConfigDTO registryConfig;
    @JsonProperty("Sockets")
    private String[] sockets;
    @JsonProperty("SwapLimit")
    private Boolean swapLimit;
    @JsonProperty("SystemTime")
    private String systemTime;
    @JsonProperty("ServerVersion")
    private String serverVersion;
    @JsonProperty("ClusterStore")
    private String clusterStore;
    @JsonProperty("ClusterAdvertise")
    private String clusterAdvertise;
    // @JsonProperty("Swarm")
    // private SwarmInfo swarm;
    @JsonProperty("Isolation")
    private String isolation;
    @JsonProperty("SecurityOptions")
    private List<String> securityOptions;
    @JsonProperty("Runtimes")
    private Map<String, RuntimeInfoDTO> runtimes;
}

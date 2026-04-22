package docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.*;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class DockerService {

    private DockerClient dockerClient;
    private boolean available = false;

    @PostConstruct
    void connect() {
        try {
            DockerClientConfig config = DefaultDockerClientConfig
                    .createDefaultConfigBuilder()
                    .build();

            ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(10)
                    .build();

            dockerClient = DockerClientImpl.getInstance(config, httpClient);

            dockerClient.pingCmd().exec();
            available = true;
        } catch (Exception e) {
            available = false;
        }
    }

    @PreDestroy
    void disconnect() {
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception ignored) {}
        }
    }

    public boolean isAvailable() {
        return available;
    }

    public DockerHostInfo getHostInfo() {
        if (!available) return null;

        try {
            Info info = dockerClient.infoCmd().exec();

            return DockerHostInfo.builder()
                    .dockerVersion(dockerClient.versionCmd().exec().getVersion())
                    .operatingSystem(info.getOperatingSystem())
                    .totalContainers(info.getContainers())
                    .runningContainers(info.getContainersRunning())
                    .stoppedContainers(info.getContainersStopped())
                    .totalImages(info.getImages())
                    .totalMemory(info.getMemTotal())
                    .cpus(info.getNCPU())
                    .build();

        } catch (Exception e) {
            return null;
        }
    }

    public List<DockerContainerDTO> getRunningContainers() {
        if (!available) return List.of();

        try {
            return dockerClient
                    .listContainersCmd()
                    .withStatusFilter(List.of("running"))
                    .exec()
                    .stream()
                    .map(container -> {
                        DockerStats stats = fetchStats(container.getId());

                        String name = container.getNames() != null && container.getNames().length > 0
                                ? container.getNames()[0].replaceFirst("/", "")
                                : "unknown";

                        String[] ports = Arrays.stream(container.getPorts())
                                .map(p -> p.getPublicPort() != null
                                        ? p.getPublicPort() + "->" + p.getPrivatePort() + "/" + p.getType()
                                        : p.getPrivatePort() + "/" + p.getType())
                                .toArray(String[]::new);

                        return toDTO(DockerContainerInfo.builder()
                                .id(container.getId())
                                .shortId(container.getId().substring(0, 12))
                                .name(name)
                                .image(container.getImage())
                                .status(container.getStatus())
                                .state(container.getState())
                                .createdAt(container.getCreated())
                                .ports(ports)
                                .labels(container.getLabels())
                                .stats(stats)
                                .build());
                    })
                    .toList();

        } catch (Exception e) {
            return List.of();
        }
    }

    private DockerStats fetchStats(String containerId) {
        AtomicReference<Statistics> statsRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        try {
            StatsCmd statsCmd = dockerClient.statsCmd(containerId).withNoStream(true);

            statsCmd.exec(new InvocationBuilder.AsyncResultCallback<Statistics>() {
                @Override
                public void onNext(Statistics stats) {
                    statsRef.set(stats);
                    latch.countDown();
                }

                @Override
                public void onError(Throwable throwable) {
                    latch.countDown();;
                }
            });

            latch.await(3, TimeUnit.SECONDS);
            statsCmd.close();

        } catch (Exception e) {
            return emptyStats();
        }

        Statistics stats = statsRef.get();
        if (stats == null) return emptyStats();

        return buildStats(stats);
    }

    private DockerStats buildStats(Statistics stats) {
        double cpuPercent = 0.0;
        try {
            CpuStatsConfig cpuStats    = stats.getCpuStats();
            CpuStatsConfig preCpuStats = stats.getPreCpuStats();

            long cpuDelta    = cpuStats.getCpuUsage().getTotalUsage()
                    - preCpuStats.getCpuUsage().getTotalUsage();
            long systemDelta = cpuStats.getSystemCpuUsage()
                    - preCpuStats.getSystemCpuUsage();
            long cpuCount    = cpuStats.getOnlineCpus() != null
                    ? cpuStats.getOnlineCpus() : 1;

            if (systemDelta > 0) {
                cpuPercent = Math.round(
                        ((double) cpuDelta / systemDelta) * cpuCount * 100.0 * 100.0
                ) / 100.0;
            }
        } catch (Exception ignored) {}

        long memUsed      = 0;
        long memLimit     = 0;
        double memPercent = 0.0;
        try {
            MemoryStatsConfig memStats = stats.getMemoryStats();
            memUsed    = memStats.getUsage();
            memLimit   = memStats.getLimit();
            memPercent = memLimit > 0
                    ? Math.round((double) memUsed / memLimit * 10000.0) / 100.0
                    : 0.0;
        } catch (Exception ignored) {}

        long netIn  = 0;
        long netOut = 0;
        try {
            Map<String, StatisticNetworksConfig> networks = stats.getNetworks();
            if (networks != null) {
                for (StatisticNetworksConfig net : networks.values()) {
                    netIn  += net.getRxBytes();
                    netOut += net.getTxBytes();
                }
            }
        } catch (Exception ignored) {}

        long blockRead  = 0;
        long blockWrite = 0;
        try {
            List<BlkioStatEntry> blkioStats = stats.getBlkioStats().getIoServiceBytesRecursive();
            if (blkioStats != null) {
                for (BlkioStatEntry entry : blkioStats) {
                    if ("Read".equalsIgnoreCase(entry.getOp()))  blockRead  += entry.getValue();
                    if ("Write".equalsIgnoreCase(entry.getOp())) blockWrite += entry.getValue();
                }
            }
        } catch (Exception ignored) {}

        return DockerStats.builder()
                .cpuPercent(cpuPercent)
                .memoryUsed(memUsed)
                .memoryLimit(memLimit)
                .memoryPercent(memPercent)
                .networkIn(netIn)
                .networkOut(netOut)
                .blockRead(blockRead)
                .blockWrite(blockWrite)
                .build();
    }

    private DockerStats emptyStats() {
        return DockerStats.builder()
                .cpuPercent(0.0)
                .memoryUsed(0)
                .memoryLimit(0)
                .memoryPercent(0.0)
                .networkIn(0)
                .networkOut(0)
                .blockRead(0)
                .blockWrite(0)
                .build();
    }

    private DockerContainerDTO toDTO(DockerContainerInfo container) {
        String role    = container.getLabels() != null
                ? container.getLabels().getOrDefault("k3d.role", "unknown")
                : "unknown";

        String cluster = container.getLabels() != null
                ? container.getLabels().getOrDefault("k3d.cluster", "unknown")
                : "unknown";

        return DockerContainerDTO.builder()
                .shortId(container.getShortId())
                .name(container.getName())
                .image(container.getImage())
                .status(container.getStatus())
                .state(container.getState())
                .role(role)
                .cluster(cluster)
                .stats(container.getStats())
                .build();
    }
}
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class DockerService {

    // how long to wait before pinging the daemon again after it was found unavailable
    private static final long RETRY_INTERVAL_MS = 30_000;
    private static final long STATS_TIMEOUT_SECONDS = 3;

    private volatile DockerClient dockerClient;
    private volatile boolean available = false;
    private volatile long lastAttemptAt = 0;

    @PostConstruct
    void init() {
        connect();
    }

    // synchronized so concurrent requests don't build several clients at once
    private synchronized void connect() {
        lastAttemptAt = System.currentTimeMillis();
        try {
            if (dockerClient != null) {
                dockerClient.pingCmd().exec();
                available = true;
                return;
            }

            DockerClientConfig config = DefaultDockerClientConfig
                    .createDefaultConfigBuilder()
                    .build();

            ApacheDockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    // stats are fetched in parallel, one connection per container
                    .maxConnections(25)
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
        return ensureAvailable();
    }

    // if Docker wasn't running when the app started (or went away later), try again —
    // throttled so a missing daemon doesn't cost a ping on every request
    private boolean ensureAvailable() {
        if (available) return true;
        if (System.currentTimeMillis() - lastAttemptAt >= RETRY_INTERVAL_MS) {
            connect();
        }
        return available;
    }

    public DockerHostInfo getHostInfo() {
        if (!ensureAvailable()) return null;

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
            available = false;
            return null;
        }
    }

    public List<DockerContainerDTO> getRunningContainers() {
        if (!ensureAvailable()) return List.of();

        List<Container> containers;
        try {
            containers = dockerClient
                    .listContainersCmd()
                    .withStatusFilter(List.of("running"))
                    .exec();
        } catch (Exception e) {
            // daemon went away — mark it so the next request retries the connection
            available = false;
            return List.of();
        }

        // fetch stats for all containers in parallel: each call takes ~1-2s on the
        // Docker side, so doing them one by one made the page take N × seconds
        Map<String, Future<DockerStats>> statsById = new HashMap<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Container container : containers) {
                statsById.put(container.getId(), executor.submit(() -> fetchStats(container.getId())));
            }

            return containers.stream()
                    .map(container -> {
                        DockerStats stats = awaitStats(statsById.get(container.getId()));

                        String name = container.getNames() != null && container.getNames().length > 0
                                ? container.getNames()[0].replaceFirst("/", "")
                                : "unknown";

                        String[] ports = Arrays.stream(Optional.ofNullable(container.getPorts()).orElse(new ContainerPort[0]))
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

    private DockerStats awaitStats(Future<DockerStats> future) {
        if (future == null) return emptyStats();
        try {
            // fetchStats has its own timeout; this is just a safety net
            return future.get(STATS_TIMEOUT_SECONDS + 1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return emptyStats();
        } catch (Exception e) {
            future.cancel(true);
            return emptyStats();
        }
    }

    private DockerStats fetchStats(String containerId) {
        AtomicReference<Statistics> statsRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        InvocationBuilder.AsyncResultCallback<Statistics> callback = new InvocationBuilder.AsyncResultCallback<>() {
            @Override
            public void onNext(Statistics stats) {
                statsRef.set(stats);
                latch.countDown();
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
            }
        };

        try (StatsCmd statsCmd = dockerClient.statsCmd(containerId).withNoStream(true)) {
            statsCmd.exec(callback);
            latch.await(STATS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return emptyStats();
        } catch (Exception e) {
            return emptyStats();
        } finally {
            // close the callback too — closing only the command left the HTTP
            // stream open whenever the latch timed out
            try {
                callback.close();
            } catch (Exception ignored) {}
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
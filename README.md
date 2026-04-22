# Heimdall

> *In Norse mythology, Heimdall is the all-seeing guardian of the Bifrost — watching over the nine realms with perfect sight, never sleeping, missing nothing.*

Heimdall is a real-time JVM monitoring dashboard for Quarkus applications. Add it to your project and instantly get a live view of your application's memory, CPU, garbage collection, threads, bean dependency graph, connection pools, and Docker containers — all in a single, beautiful dashboard.

---

## How it works

Heimdall runs **inside your JVM process**. When added as a dependency, it shares the same JVM as your application and reads metrics directly from the JVM's built-in management APIs — no agents, no sidecars, no external processes.

```
Your Application JVM
  ├── Your application code
  └── Heimdall
        ├── Reads JVM metrics via ManagementFactory MXBeans
        ├── Collects a snapshot every 3 seconds
        ├── Serves REST API at /heimdall/api/*
        └── Serves the dashboard UI at /heimdall
```

The dashboard is a React SPA bundled directly into the Heimdall JAR and served as static resources. No separate frontend server required.

---

## Project structure

Heimdall is organized as a Maven multi-module project:

```
heimdall/
├── heimdall-core/        ← Quarkus application — all JVM monitoring, REST endpoints, React UI
├── heimdall-docker/      ← Optional module — Docker container monitoring
└── heimdall-ui/          ← React + Vite frontend (builds into heimdall-core resources)
```

### heimdall-core

The main runnable module. Contains all JVM metric collection, REST endpoints, CDI bean graph, connection detection, metrics history, and the React dashboard. This is what you run with `quarkus:dev` and what users add as a dependency.

### heimdall-docker

An optional library module that adds Docker container monitoring. When added as a dependency to `heimdall-core`, its REST endpoints are automatically registered by Quarkus — no extra configuration needed. Users who don't need Docker monitoring simply don't include this module.

```xml
<!-- JVM monitoring only -->
<dependency>heimdall-core</dependency>

<!-- JVM + Docker monitoring -->
<dependency>heimdall-core</dependency>
<dependency>heimdall-docker</dependency>
```

### heimdall-ui

The React + Vite frontend. During development it runs independently on port 5173 with a proxy to Quarkus on port 8080. For production it builds into `heimdall-core/src/main/resources/META-INF/resources` and gets served directly by Quarkus.

---

## What it measures

### Heap Memory
Reads from `MemoryMXBean`:

| Metric | Description |
|---|---|
| In Usage | Currently used heap in MB |
| Committed | Memory currently reserved by the JVM |
| Max Allowed | Maximum heap size configured |
| Initial | Heap size requested at JVM startup |
| Usage % | Percentage of max heap currently in use |

### Garbage Collection
Reads from `GarbageCollectorMXBeans` — one entry per active collector:

| Metric | Description |
|---|---|
| Name | Collector name (e.g. G1 Young Generation) |
| Collection Count | Total collections since JVM start |
| Collection Time | Total time spent in GC in milliseconds |

### Threads
Reads from `ThreadMXBean` with per-thread state inspection:

| Metric | Description |
|---|---|
| Active | Current live thread count |
| Blocked | Threads waiting on a monitor lock |
| Waiting | Threads waiting indefinitely |
| Timed Waiting | Threads waiting with a timeout |
| Peak | Highest thread count since JVM start |

### CPU & System
Reads from `com.sun.management.OperatingSystemMXBean`:

| Metric | Description |
|---|---|
| Process CPU Load | CPU used by this JVM process (%) |
| System CPU Load | Total CPU used across the machine (%) |
| Available Processors | Number of CPU cores |
| Total Memory | Total physical RAM in MB |

### JVM Info
Reads from `RuntimeMXBean` and system properties:

| Metric | Description |
|---|---|
| Uptime | Time since JVM started (formatted) |
| PID | Process ID |
| JVM Name | JVM implementation name |
| Java Version | Java runtime version |

### Bean Dependency Graph
Reads from CDI's `BeanManager` to build a live graph of your application's beans and their dependencies:

- Every CDI bean as a node, colored by scope
- Dependency edges built from injection points
- Supports `@Inject` field injection, constructor injection, and Lombok's `@RequiredArgsConstructor` pattern
- Focus mode — click any bean to highlight its direct dependencies and dependents
- Search and scope filtering to navigate large applications

### Connection Pool Monitoring
Auto-detects and monitors connection pools via JMX and CDI:

| Technology | Detection Method | Metrics |
|---|---|---|
| HikariCP | JMX MBeans | active, idle, total, pending, usage % |
| Kafka Consumer | JMX MBeans | records lag, fetch rate, commit rate, partitions |
| Kafka Producer | JMX MBeans | send rate, error rate, latency avg/max |
| Redis | JMX + CDI + config | pool size, active, idle connections |
| RabbitMQ | JMX + CDI + management API | published, consumed, failed messages, connections |

### Docker Container Monitoring
Connects to the Docker socket and monitors all running containers:

| Metric | Description |
|---|---|
| Name | Container name |
| Image | Docker image and tag |
| Role | Container role (server, agent, loadbalancer) |
| Cluster | Cluster name from labels |
| CPU % | Container CPU usage percentage |
| Memory | Used / limit and percentage |
| Network I/O | Bytes in and out |
| Block I/O | Disk read and write |

Containers are grouped by cluster and color-coded by role. Provided by the optional `heimdall-docker` module.

### Metrics History
Every 3 seconds Heimdall captures a full metrics snapshot and keeps the last 60 readings in memory. The dashboard plots these as live area charts with a switchable metric selector, giving you a rolling 3-minute window of your application's behavior.

---

## Dashboard

The Heimdall dashboard provides:

- **Live metric cards** — memory, CPU, threads, and GC at a glance
- **JVM status bar** — uptime, PID, Java version, JVM name always visible in the header
- **Interactive history chart** — switch between memory, CPU, threads, and GC to see trends
- **Bean dependency graph** — visualize your CDI bean tree with focus, search, and scope filters
- **Connection panel** — auto-detected connection pools with live stats
- **Docker page** — full-page container dashboard grouped by cluster
- **Bifrost loader** — animated loading screen while connecting to the JVM
- **Auto-refresh** — all data updates every 3 seconds automatically

---

## Getting started

### Requirements

- Java 21+
- Quarkus 3.x
- Maven

### Running in development

```bash
# build all modules
./mvnw clean install -DskipTests

# run the dashboard
cd heimdall-core
../mvnw quarkus:dev
```

Visit `http://localhost:8080/heimdall` for the dashboard.

### Running the frontend separately

```bash
cd heimdall-ui
npm install
npm run dev
```

Visit `http://localhost:5173` — the Vite dev server proxies API calls to Quarkus on port 8080.

### Optional configuration

```properties
# How often metrics are collected (default: 3s)
heimdall.scheduler.interval=3s

# How many snapshots to keep in history (default: 60)
heimdall.history.limit=60
```

---

## API reference

| Method | Endpoint | Description |
|---|---|---|
| GET | `/heimdall/api/metrics` | Current JVM snapshot |
| GET | `/heimdall/api/metrics/history` | Last N metric snapshots |
| GET | `/heimdall/api/beans/graph` | CDI bean dependency graph |
| GET | `/heimdall/api/connections` | Detected connection pools |
| GET | `/heimdall/api/docker/containers` | Running Docker containers |
| GET | `/heimdall/api/docker/host` | Docker host info |
| GET | `/heimdall/api/health` | Heimdall agent health *(coming soon)* |
| GET | `/heimdall/api/alerts` | Active alerts *(coming soon)* |
| GET | `/heimdall/api/threads/dump` | Thread dump on demand *(coming soon)* |

---

## Roadmap

### Phase 2 — Bean monitoring

**`@HeimdallMonitor` interceptor**

Annotate any CDI bean to enable method-level instrumentation. Heimdall intercepts every method call and records call count, average/min/max execution time, error count, and last called timestamp — all visible in the dashboard per bean and per method.

```java
@HeimdallMonitor
@ApplicationScoped
public class OrderService {
    public Order createOrder(CreateOrderRequest req) { ... }
}
```

Bean nodes in the dependency graph will be color-coded by health — green if healthy, amber if slow, red if errors detected.

### Phase 3 — WebSocket live push

Replace the current polling model with a WebSocket connection. The server pushes metric snapshots as they are collected, eliminating unnecessary HTTP overhead and making the dashboard truly real-time with instant updates.

### Phase 4 — Alerting

Configure thresholds in `application.properties`:

```properties
heimdall.alert.heap.threshold=80
heimdall.alert.cpu.threshold=90
heimdall.alert.thread.blocked.threshold=5
heimdall.alert.pool.usage.threshold=85
```

Alerts appear as badges in the dashboard header and as a dedicated alert panel showing current and recent triggered alerts. Browser notifications via the Web Notifications API are also planned.

### Phase 5 — Thread dump on demand

Request a full thread dump from the dashboard at any time — useful for diagnosing deadlocks and performance issues without restarting the application.

### Phase 6 — Kubernetes monitoring (`heimdall-kubernetes`)

A new optional module that connects to the Kubernetes API to monitor pods, deployments, and services. Auto-detects whether running in-cluster or locally via kubeconfig.

```xml
<dependency>heimdall-kubernetes</dependency>
```

Dashboard additions:
- Pod list with CPU and memory per pod via metrics-server
- Deployment health — replica counts and rollout status
- Service discovery — all services in the namespace
- Automatic detection of k3d, minikube, and production clusters

### Phase 7 — Quarkus extension

Convert `heimdall-core` into a proper Quarkus extension with `deployment` and `runtime` modules. Bundle the React UI into the JAR via the `frontend-maven-plugin`. Publish to Maven Central.

```xml
<!-- zero config, just add the dependency -->
<dependency>
    <groupId>io.heimdall</groupId>
    <artifactId>heimdall-quarkus</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Phase 8 — Spring Boot starter (`heimdall-spring`)

A Spring Boot auto-configuration adapter that wires Heimdall into Spring applications. Uses `ApplicationContext` instead of `BeanManager` for bean graph, and Spring Boot Actuator endpoints for health integration.

### Long term

- GraalVM native image support
- Multi-instance monitoring — observe multiple JVM processes from one dashboard
- Heap dump trigger from the UI
- Metrics export to CSV / JSON
- Light theme toggle
- `heimdall-standalone` — a thin wrapper that connects to a remote JMX or Actuator endpoint without being embedded in the target app

---

## Architecture

```
heimdall/
├── heimdall-core/
│   └── src/main/java/
│       ├── services/
│       │   └── MetricsService.java        — JVM metric collection
│       ├── history/
│       │   ├── MetricsHistory.java        — rolling snapshot buffer
│       │   └── MetricsSnapshot.java       — timestamped metric wrapper
│       ├── collectors/
│       │   └── MetricsCollector.java      — scheduled collection every 3s
│       ├── beans/
│       │   ├── BeanService.java           — CDI graph builder
│       │   ├── BeanGraph.java             — graph model
│       │   ├── BeanNode.java              — node model
│       │   └── BeanEdge.java              — edge model
│       ├── connections/
│       │   ├── ConnectionService.java     — connection pool detection
│       │   ├── ConnectionInfo.java        — connection model
│       │   └── detect/
│       │       ├── HikariCPConnection.java
│       │       ├── KafkaConnection.java
│       │       ├── RedisConnection.java
│       │       └── RabbitMQConnection.java
│       ├── health/
│       │   └── HeimdallHealthCheck.java   — SmallRye health check
│       ├── exceptions/
│       │   ├── ErrorResponse.java
│       │   └── GlobalExceptionMapper.java
│       └── resources/
│           ├── MetricsResource.java       — metrics REST endpoints
│           ├── BeanResource.java          — bean graph REST endpoints
│           └── ConnectionResource.java    — connection REST endpoints
│
├── heimdall-docker/
│   └── src/main/java/docker/
│       ├── DockerService.java             — Docker socket connection
│       ├── DockerResource.java            — Docker REST endpoints
│       ├── DockerContainerInfo.java       — full container model
│       ├── DockerContainerDTO.java        — trimmed response model
│       ├── DockerStats.java               — container stats model
│       └── DockerHostInfo.java            — Docker host model
│
└── heimdall-ui/
    └── src/
        ├── components/
        │   ├── MemoryInfo.tsx             — heap memory card
        │   ├── CpuInfo.tsx                — CPU card
        │   ├── ThreadInfo.tsx             — threads card
        │   ├── GarbageCollectorInfo.tsx   — GC card
        │   ├── MetricsChart.tsx           — switchable history chart
        │   ├── BeanGraph.tsx              — interactive dependency graph
        │   ├── DockerDashboard.tsx        — Docker containers page
        │   └── BifrostLoader.tsx          — animated loading screen
        └── types/
            └── metrics.ts                 — TypeScript type definitions
```

---

## Why Heimdall?

Most JVM monitoring solutions require external infrastructure — a Prometheus server, a Grafana instance, an APM agent. Heimdall is different: it lives inside your application, requires zero external dependencies, and gives you meaningful insight with a single dependency addition.

It is especially useful for:
- Development and debugging — understand what your app is doing right now
- Staging environments — catch memory leaks and thread issues before production
- Small teams — get observability without DevOps infrastructure
- Containerized workloads — monitor JVM internals and Docker containers from the same dashboard

---

## Contributing

Heimdall is in active development. Contributions, issues, and feature requests are welcome.

---

## License

MIT
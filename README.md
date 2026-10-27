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
├── heimdall-core/        ← library — JVM monitoring, tracing, REST endpoints + the bundled dashboard
├── heimdall-docker/      ← optional library — Docker container monitoring
├── heimdall-ui/          ← React + Vite dashboard (packaged into the heimdall-core jar)
└── heimdall-demo/        ← example host application, used to develop and try Heimdall
```

### heimdall-core

The library applications add as a dependency. Contains all JVM metric collection, request/method tracing, REST endpoints, CDI bean graph, connection detection, metrics history — and the built dashboard, which the build places in `META-INF/resources/heimdall` so the host application serves it at `/heimdall/`. All classes live under `com.fischer.heimdall.*`.

It ships no `application.properties`: every `heimdall.*` setting has a default, and nothing in the jar changes the host application's configuration.

### heimdall-docker

An optional library that adds Docker container monitoring. Add it next to `heimdall-core` and its endpoints are registered automatically; leave it out if you don't need it.

### heimdall-ui

The React + Vite dashboard. `./mvnw install` builds it (through `frontend-maven-plugin`, which downloads its own Node) and packages it into the `heimdall-core` jar. During development it can also run on its own at `http://localhost:5173/heimdall/`, proxying API calls to Quarkus on port 8080.

### heimdall-demo

A small Quarkus application (an in-memory orders API) that depends on Heimdall exactly like a real project would. It is what you run while developing Heimdall, and it holds the dev-only settings (CORS for the Vite dev server, Swagger UI).

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

Full thread dumps are also available on demand — every live thread's state, stack trace, held/awaited locks, and any detected deadlocks, captured via `ThreadMXBean.dumpAllThreads()`.

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
| Agroal (Quarkus datasources) | Micrometer gauges (`quarkus.datasource.metrics.enabled=true`) | active, idle, pending, max used, leaks, usage % |
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
- A Quarkus 3.x application (built with Maven)

Node is **not** required — the Maven build downloads its own copy to build the dashboard.

### Adding Heimdall to your project

> Step-by-step guide with a test checklist (in Portuguese): [TESTING.md](TESTING.md)

Releases are published to GitHub Packages (see [TESTING.md](TESTING.md) for the one-time `settings.xml` token setup) and attached to [GitHub Releases](https://github.com/FischerRobson/heimdall/releases). To use the latest code instead, install it into your local Maven repository:

```bash
git clone git@github.com:FischerRobson/heimdall.git
cd heimdall
./mvnw clean install -DskipTests
```

Then add it to your application's `pom.xml`:

```xml
<dependency>
    <groupId>com.fischer</groupId>
    <artifactId>heimdall-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- optional: Docker container monitoring -->
<dependency>
    <groupId>com.fischer</groupId>
    <artifactId>heimdall-docker</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Start your application (`./mvnw quarkus:dev`) and open **`http://localhost:8080/heimdall/`** (with the trailing slash).

To see your own methods as spans in the trace waterfall, annotate the classes with `@HeimdallMonitor`:

```java
import com.fischer.heimdall.tracing.HeimdallMonitor;

@HeimdallMonitor
@ApplicationScoped
public class OrderService { ... }
```

Every HTTP request to your application is traced either way; the annotation adds the method-level spans (classes named `*Resource`, `*Controller`, `*Service`, `*Repository` or `*Dao`, or in the packages listed in `heimdall.tracing.packages`). `Heimdall.tag("orderId", id)` attaches business context to the current span.

**What comes with the dependency:** Quarkus REST + Jackson, WebSockets Next, Scheduler, SmallRye Health (`/q/health`) and Micrometer with the Prometheus registry (`/q/metrics`). Agroal pool stats need `quarkus.datasource.metrics.enabled=true` in your application.

> Heimdall exposes JVM internals, request headers/bodies and heap dumps with no authentication. Use it in development and staging only.

### Developing Heimdall

```bash
./mvnw clean install -DskipTests     # builds all modules, including the dashboard
cd heimdall-demo
../mvnw quarkus:dev                  # demo app with Heimdall embedded
```

- Dashboard: `http://localhost:8080/heimdall/`
- Demo endpoints to generate traces: `GET /orders`, `GET /orders/1`, `POST /orders`, `GET /orders/report` (slow), `GET /orders/fail` (error) — or use the Swagger UI at `http://localhost:8080/q/swagger-ui`

For hot reload on the dashboard, `./start.sh` runs the demo app and the Vite dev server together; open `http://localhost:5173/heimdall/`. To skip the UI build in Maven and reuse an existing `heimdall-ui/dist`, pass `-Dskip.npm -Dskip.installnodenpm`.

### Optional configuration

```properties
# how often metrics are collected
heimdall.scheduler.interval=3s
# metric snapshots / request traces kept in memory
heimdall.history.limit=60
heimdall.traces.limit=100
# stack frames per thread, and thread dumps kept for comparison
heimdall.threaddump.max-depth=100
heimdall.threaddump.history-limit=10
# restrict method tracing to these packages (comma-separated)
heimdall.tracing.packages=com.example
```

---

## API reference

| Method | Endpoint | Description |
|---|---|---|
| GET | `/heimdall/api/metrics` | Current JVM snapshot |
| GET | `/heimdall/api/metrics/history` | Last N metric snapshots |
| POST | `/heimdall/api/metrics/gc` | Trigger a garbage collection |
| GET | `/heimdall/api/metrics/heapdump?live=true` | Download a `.hprof` heap dump (pauses the JVM — dev/staging only) |
| GET | `/heimdall/api/beans/graph` | CDI bean dependency graph |
| GET | `/heimdall/api/connections` | Detected connection pools |
| GET | `/heimdall/api/traces` | Recent request traces (Heimdall's own requests excluded) |
| GET | `/heimdall/api/traces/errors` | Traces with status ≥ 400 |
| GET | `/heimdall/api/traces/method/{method}` | Traces for one HTTP method |
| GET | `/heimdall/api/docker/containers` | Running Docker containers |
| GET | `/heimdall/api/docker/host` | Docker host info (503 if the daemon is unreachable) |
| GET | `/heimdall/api/health` | Heimdall agent health |
| GET | `/heimdall/api/alerts` | Active alerts *(coming soon)* |
| GET | `/heimdall/api/threads/dump` | Capture a thread dump (also stored in history) |
| GET | `/heimdall/api/threads/dumps` | Stored thread dumps, newest first (summaries) |
| GET | `/heimdall/api/threads/dumps/{id}` | One stored thread dump |
| WS | `/heimdall/ws/metrics` | Live metric snapshots (history on connect, then one per interval) |

Heimdall's routes carry the `/heimdall/api` prefix themselves — it does not set `quarkus.rest.path`, so the host application's endpoints keep their own paths.

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

### Phase 3 — WebSocket live push *(done for the dashboard metrics; other pages still poll)*

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
heimdall-core/src/main/java/com/fischer/heimdall/
├── metrics/       MetricsService (JVM MXBeans), MetricsResource, MetricsSocket (WebSocket),
│                  HeapDumpService
├── collectors/    MetricsCollector — scheduled snapshot every heimdall.scheduler.interval
├── history/       MetricsHistory — rolling snapshot buffer
├── tracing/       TraceFilter (per request), HeimdallInterceptor (@HeimdallMonitor spans),
│                  TraceContext, TraceRegistry, Heimdall.tag() API
├── beans/         BeansService — CDI bean dependency graph
├── connections/   ConnectionsService + detect/ (Agroal, HikariCP, Kafka, Redis, RabbitMQ)
├── threads/       ThreadDumpService, ThreadDumpHistory
├── health/        SmallRye readiness check + /heimdall/api/health
└── exceptions/, utils/

heimdall-docker/src/main/java/com/fischer/heimdall/docker/
└── DockerService (docker-java), DockerResource

heimdall-ui/src/
├── pages/         dashboard, docker, beans, trace, connections, threads (lazy-loaded)
├── components/    metric cards, MetricsChart, CopyButton, BifrostLoader
└── utils/         client-side export (CSV/JSON)

heimdall-demo/src/main/java/com/example/demo/orders/
└── OrderResource → OrderService → OrderRepository (in-memory, with artificial latency)
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
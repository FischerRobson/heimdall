# Heimdall

> *In Norse mythology, Heimdall is the all-seeing guardian of the Bifrost — watching over the nine realms with perfect sight, never sleeping, missing nothing.*

Heimdall is a real-time JVM monitoring dashboard for Quarkus applications. Add it to your project and instantly get a live view of your application's memory, CPU, garbage collection, threads, and bean dependency graph — all in a single, beautiful dashboard.

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

## What it measures

### Heap Memory
Heimdall reads from `MemoryMXBean` and reports:

| Metric | Description |
|---|---|
| In Usage | Currently used heap in MB |
| Committed | Memory currently reserved by the JVM |
| Max Allowed | Maximum heap size configured |
| Initial | Heap size requested at JVM startup |
| Usage % | Percentage of max heap currently in use |

### Garbage Collection
Reads from `GarbageCollectorMXBeans` — one entry per active collector (e.g. G1 Young Generation, G1 Old Generation):

| Metric | Description |
|---|---|
| Name | Collector name |
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

### Bean Dependency Graph
Reads from CDI's `BeanManager` to build a live graph of your application's beans and their dependencies:

- Every CDI bean as a node, colored by scope
- Dependency edges built from injection points
- Supports `@Inject` field injection, constructor injection, and Lombok's `@RequiredArgsConstructor` pattern
- Focus mode — click any bean to highlight its direct dependencies and dependents
- Search and scope filtering to navigate large applications

### Metrics History
Every 3 seconds Heimdall captures a full metrics snapshot and keeps the last 60 readings in memory. The dashboard plots these as live area charts, giving you a rolling 3-minute window of your application's behavior.

---

## Dashboard

The Heimdall dashboard provides:

- **Live metric cards** — memory, CPU, threads, and GC at a glance
- **Interactive history chart** — switch between metrics to see trends over time
- **Bean graph** — visualize your CDI bean dependency tree with focus, search, and scope filters
- **Auto-refresh** — data updates every 3 seconds automatically

---

## Getting started

### Requirements
- Java 21+
- Quarkus 3.x
- Maven or Gradle

### Installation

Add Heimdall to your `pom.xml`:

```xml
<dependency>
    <groupId>io.heimdall</groupId>
    <artifactId>heimdall-quarkus</artifactId>
    <version>1.0.0</version>
</dependency>
```

Start your application and visit:

```
http://localhost:8080/heimdall
```

That's it. No configuration required.

### Optional configuration

You can customize Heimdall's behavior in `application.properties`:

```properties
# How often metrics are collected (default: 3s)
heimdall.scheduler.interval=3s

# How many snapshots to keep in history (default: 60)
heimdall.history.limit=60

# Heap usage alert threshold in % (coming soon)
heimdall.alert.heap.threshold=80
```

---

## API reference

All endpoints are available under `/heimdall/api/`:

| Method | Endpoint | Description |
|---|---|---|
| GET | `/heimdall/api/metrics` | Current JVM snapshot |
| GET | `/heimdall/api/metrics/history` | Last N metric snapshots |
| GET | `/heimdall/api/beans/graph` | CDI bean dependency graph |
| GET | `/heimdall/api/health` | Heimdall agent health status *(coming soon)* |
| GET | `/heimdall/api/connections` | Connection pool stats *(coming soon)* |
| GET | `/heimdall/api/alerts` | Active alerts *(coming soon)* |

---

## Roadmap

### Coming soon

**Bean monitoring with `@HeimdallMonitor`**
Annotate any CDI bean to enable method-level instrumentation. Heimdall will track call count, average/min/max execution time, error count, and last called timestamp — all visible in the dashboard per bean and per method.

```java
@HeimdallMonitor
@ApplicationScoped
public class OrderService {
    public Order createOrder(CreateOrderRequest req) { ... }
}
```

**WebSocket live push**
Replace the current polling model with a WebSocket connection. The server pushes metric snapshots as they are collected, making the dashboard truly real-time with no unnecessary HTTP overhead.

**Connection pool monitoring**
Auto-detect and monitor HikariCP, Redis, and Kafka connection pools via JMX. No configuration needed — Heimdall detects which pools are present at startup.

**Alerting**
Configure thresholds for heap usage, CPU load, blocked threads, and connection pool saturation. Alerts appear in the dashboard and optionally trigger browser notifications.

**Thread dump on demand**
Request a full thread dump from the dashboard at any time — useful for diagnosing deadlocks and performance issues in production.

### Long term

- Spring Boot starter (`heimdall-spring`)
- GraalVM native image support
- Multi-instance monitoring — observe multiple JVM processes from one dashboard
- Heap dump trigger from the UI
- Metrics export to CSV / JSON
- Light theme

---

## Architecture

```
heimdall/
├── src/main/java/
│   ├── services/
│   │   └── MetricsService.java        — JVM metric collection
│   ├── history/
│   │   ├── MetricsHistory.java        — rolling snapshot buffer
│   │   └── MetricsSnapshot.java       — timestamped metric wrapper
│   ├── collectors/
│   │   └── MetricsCollector.java      — scheduled collection every 3s
│   ├── beans/
│   │   ├── BeanService.java           — CDI graph builder
│   │   ├── BeanGraph.java             — graph model
│   │   ├── BeanNode.java              — node model
│   │   └── BeanEdge.java              — edge model
│   └── resources/
│       ├── MetricsResource.java       — metrics REST endpoints
│       └── BeanResource.java          — bean graph REST endpoints
│
└── heimdall-ui/
    └── src/
        ├── components/
        │   ├── MemoryInfo.tsx          — heap memory card
        │   ├── CpuInfo.tsx             — CPU card
        │   ├── ThreadInfo.tsx          — threads card
        │   ├── GarbageCollectorInfo.tsx — GC card
        │   ├── MetricsChart.tsx        — switchable history chart
        │   └── BeanGraph.tsx           — interactive dependency graph
        └── types/
            └── metrics.ts              — TypeScript type definitions
```

---

## Why Heimdall?

Most JVM monitoring solutions require external infrastructure — a Prometheus server, a Grafana instance, an APM agent. Heimdall is different: it lives inside your application, requires zero external dependencies, and gives you meaningful insight with a single dependency addition.

It is especially useful for:
- Development and debugging — understand what your app is doing right now
- Staging environments — catch memory leaks and thread issues before production
- Small teams — get observability without DevOps infrastructure

---

## Contributing

Heimdall is in active development. Contributions, issues, and feature requests are welcome.

---

## License

MIT
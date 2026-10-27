# Como testar o Heimdall

Guia prático para buildar o Heimdall, rodar o app de exemplo e adicioná-lo a outro projeto Quarkus.

Em 26/10/2026, o build, os testes e o demo foram verificados assim: endpoints, traces, WebSocket, heap dump e proxy do Vite via `curl`/Node. A interface em si ainda não foi conferida num navegador — a tabela do passo 2 é o checklist para isso.

## Pré-requisitos

- **Java 21+** (testado com GraalVM 21 via sdkman)
- **Maven**: não precisa instalar, o projeto usa o wrapper `./mvnw`
- **Node**: não precisa para o build (o Maven baixa o próprio Node para buildar a UI). Só é necessário para o modo de desenvolvimento da UI com hot reload (`npm run dev`); testado com Node 24 via nvm

> Com sdkman/nvm, abra um terminal novo (ou rode `source ~/.sdkman/bin/sdkman-init.sh` e `nvm use`) para `java` e `node` estarem no PATH.

---

## 1. Buildar

```bash
cd heimdall
./mvnw clean install
```

O comando builda a UI, compila os módulos, roda os testes e instala `heimdall-core` e `heimdall-docker` no repositório Maven local (`~/.m2`).

**Esperado:** `BUILD SUCCESS` e `Tests run: 25, Failures: 0`. Leva ~1,5 min na primeira vez.

Variações úteis:

```bash
./mvnw clean install -DskipTests                          # sem rodar os testes
./mvnw install -Dskip.npm -Dskip.installnodenpm           # reaproveita heimdall-ui/dist, sem rebuildar a UI
```

---

## 2. Rodar o app de exemplo (heimdall-demo)

O `heimdall-demo` é uma API de pedidos em memória que usa o Heimdall como dependência, do mesmo jeito que um projeto real faria.

```bash
cd heimdall-demo
../mvnw quarkus:dev
# ou, com o jar já buildado:
java -jar target/quarkus-app/quarkus-run.jar
```

- **Dashboard:** http://localhost:8080/heimdall/ (`/heimdall` sem barra redireciona)
- **Swagger UI do demo:** http://localhost:8080/q/swagger-ui (só em dev mode)

### Roteiro de teste

Gere tráfego no demo:

```bash
curl localhost:8080/orders                 # lista
curl localhost:8080/orders/1               # um pedido (tag orderId no span)
curl localhost:8080/orders/999             # 404
curl localhost:8080/orders/report          # lento (~0,4–0,9 s)
curl localhost:8080/orders/fail            # 500 (exceção não tratada)
curl -X POST localhost:8080/orders -H 'Content-Type: application/json' \
     -d '{"customer":"dani","total":42.5,"password":"hunter2"}'    # 201
curl -X POST localhost:8080/orders -H 'Content-Type: application/json' \
     -d '{"total":1}'                                                # 400
```

Depois confira no dashboard:

| Onde | O que verificar |
|---|---|
| Cabeçalho | `● live`, uptime avançando a cada 3 s; derrube o app e deve virar `● offline` |
| Dashboard | cards de memória/CPU/threads/GC e gráfico atualizando sozinhos (WebSocket) |
| Dashboard → `csv` / `json` | baixa o histórico de métricas |
| Card de memória → `heap dump` | baixa um `.hprof` (pausa a JVM por um instante) |
| Card de memória → `run gc` | mostra quanto foi liberado |
| **trace** | um trace por request do demo; **nenhum** trace de `/heimdall/api/*` |
| trace → `POST /orders` | aba *request*: body com `"password":"***"` |
| trace → `GET /orders/1` | waterfall `OrderResource.get` → `OrderService.get` → `OrderRepository.findById`, com tag `orderId` |
| trace → `GET /orders/fail` | status 500 e os spans em vermelho com `payment provider unavailable (simulated)` |
| trace → `GET /orders/report` | span longo no waterfall |
| **beans** | só `OrderResource → OrderService → OrderRepository` (os beans do Heimdall ficam de fora) |
| **threads** | `capture dump` duas vezes → histórico → `vs` compara os dois; `export txt` gera formato jstack; `copy` na stack trace |
| **conns** | vazio no demo (não tem banco/Kafka/Redis/RabbitMQ) |
| **docker** | lista os containers se o Docker estiver rodando; sem Docker, "no running containers" |

Checagens rápidas pela API:

```bash
curl -s localhost:8080/heimdall/api/traces | head -c 300        # só paths do demo
curl -s localhost:8080/heimdall/api/traces/method/POST
curl -s localhost:8080/heimdall/api/docker/host                 # 503 se o Docker não estiver de pé
curl -s localhost:8080/q/health                                 # inclui o check "heimdall"
```

---

## 3. Adicionar a outro projeto Quarkus

Depois do `./mvnw install` do passo 1, no `pom.xml` do seu projeto:

```xml
<dependency>
    <groupId>com.fischer</groupId>
    <artifactId>heimdall-core</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>

<!-- opcional: monitoramento de containers Docker -->
<dependency>
    <groupId>com.fischer</groupId>
    <artifactId>heimdall-docker</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Para os métodos virarem spans no waterfall, anote as classes:

```java
import com.fischer.heimdall.tracing.HeimdallMonitor;

@HeimdallMonitor
@ApplicationScoped
public class OrderService { ... }
```

Sem a anotação, cada request HTTP ainda vira um trace, mas sem os spans dos métodos. Classes anotadas precisam terminar em `Resource`, `Controller`, `Service`, `Repository` ou `Dao`, ou estar num pacote de `heimdall.tracing.packages`.

Para ver o pool do banco (Agroal) na aba **conns**, habilite no `application.properties` do seu projeto:

```properties
quarkus.datasource.metrics.enabled=true
```

Suba o projeto (`./mvnw quarkus:dev`) e abra http://localhost:8080/heimdall/.

**O que entra junto com a dependência:** Quarkus REST + Jackson, WebSockets Next, Scheduler, SmallRye Health (`/q/health`) e Micrometer com registry Prometheus (`/q/metrics`). O Heimdall não traz `application.properties` próprio e não muda as rotas do seu projeto.

> ⚠️ Sem autenticação: o dashboard expõe headers, bodies e heap dumps. Use só em dev/staging.

---

## 4. Desenvolver a UI com hot reload

```bash
./start.sh
```

O script instala os módulos (sem rebuildar a UI), sobe o demo na porta 8080 e o Vite na 5173. Abra **http://localhost:5173/heimdall/**: as chamadas a `/heimdall/api` e `/heimdall/ws` vão por proxy para o Quarkus.

Ou manualmente, em dois terminais:

```bash
cd heimdall-demo && ../mvnw quarkus:dev
cd heimdall-ui && npm install && npm run dev
```

---

## 5. Rodar só os testes

```bash
./mvnw verify                                   # backend (25 testes)
cd heimdall-ui && npx tsc --noEmit -p .         # type check do frontend
cd heimdall-ui && npm run build                 # build do frontend
```

O CI (`.github/workflows/ci.yml`) roda os mesmos passos em push para `master` e em PRs.

---

## Problemas comuns

| Sintoma | Causa / solução |
|---|---|
| `java: command not found` / `node: command not found` | sdkman/nvm não carregados no shell; abra um terminal novo |
| Dashboard 404 | confira se o jar foi buildado sem `-Dskip.npm` pelo menos uma vez (precisa existir `heimdall-ui/dist`) |
| Trace sem spans de métodos | falta `@HeimdallMonitor` na classe, ou o nome não segue a convenção (veja o passo 3) |
| Aba conns sem o banco | falta `quarkus.datasource.metrics.enabled=true` |
| `/heimdall/api/docker/host` → 503 | Docker não está rodando ou o socket não está acessível |
| Porta 8080 ou 5173 ocupada | outro processo rodando; `quarkus.http.port=8081` muda a do Quarkus (ajuste o proxy em `vite.config.ts`) |

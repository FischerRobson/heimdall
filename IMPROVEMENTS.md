# Melhorias — Heimdall

Estado do projeto após a revisão de código de outubro/2026 e plano das próximas etapas. Os itens concluídos ficam registrados com o commit correspondente; o restante está em ordem de prioridade.

> Build, testes (25) e demo validados rodando em 26/10/2026 — ver `TESTING.md`.

---

## 1. Concluído nesta revisão

### Bugs corrigidos — `Fix bugs found in code review`

| Prioridade | Bug | Onde |
|---|---|---|
| Alta | Waterfall de traces posicionava spans com `startTime % duration` (barras em posições aleatórias) | `TracePage.tsx` |
| Alta | Traces do próprio Heimdall lotavam o registro — o filtro por path nunca batia | `TraceFilter.java` |
| Alta | `NumberFormatException` em locale com vírgula decimal (pt_BR) ao calcular % do heap | `MetricsService.java` |
| Alta | Detecção do RabbitMQ via JMX sempre vazia (`ClassCastException` engolida) | `RabbitMQConnection.java` |
| Alta | `/traces/method/{method}` lia `@QueryParam` → NPE → 500 | `TraceResource.java` |
| Média | Span ficava na pilha quando o método lançava `Error`; exceção sem mensagem não marcava erro | `HeimdallInterceptor.java` |
| Média | Chamada à API de management do RabbitMQ sem timeout (travava `/connections`) | `RabbitMQConnection.java` |
| Média | Docker: sem reconexão, stats sequenciais (N × segundos), callback não fechado, `/docker/host` inexistente | `DockerService.java`, `DockerResource.java` |
| Média | Health check `@Liveness` falhava no startup (Kubernetes reiniciaria o pod) | `HeimdallHealthCheck.java` |
| Média | Pool Agroal (o que o Quarkus usa) não era detectado — só HikariCP | `AgroalConnection.java` (novo) |
| Baixa | Cabeçalho com uptime parado e "live" fixo | `App.tsx` |
| Baixa | Página Docker sem auto-refresh | `DockerPage.tsx` |
| Baixa | NPE com `e.getMessage()` nulo em `Map.of` | Hikari/Kafka |
| Baixa | Beans com mesmo nome simples viravam um nó só | `BeansService.java`, `BeansPage.tsx` |
| Baixa | `heimdall-docker` não era opcional de fato | `heimdall-core/pom.xml` |

**Correção ao levantamento anterior:** o `findAny()` em `MetricsBroadcaster` **não era bug** — `connection.broadcast()` já envia para todas as conexões do endpoint, então várias abas recebem updates.

### Rotas — `Prefix Heimdall routes in @Path instead of quarkus.rest.path`

`quarkus.rest.path=/heimdall/api/` era global e moveria todos os endpoints da aplicação hospedeira. Agora cada resource do Heimdall declara o prefixo no próprio `@Path`.

### Funcionalidades — `Add heap dumps, thread dump history, exports and lazy-loaded pages`

- Heap dump pela UI (`GET /heimdall/api/metrics/heapdump`)
- Histórico de thread dumps (últimos 10) com comparação entre dois dumps
- Exportação: métricas (CSV/JSON), traces (JSON), thread dump (JSON / texto estilo jstack)
- Botões de copiar em stack traces, bodies e erros de span
- Code-splitting das páginas com `React.lazy`

### Testes e CI — `Add first test suite and GitHub Actions CI`

Primeira suíte (`heimdall-core/src/test`) cobrindo API, interceptor, `TraceContext`, `MetricsService`, Agroal, histórico de thread dumps e heap dump. CI no GitHub Actions com `mvnw verify` + build do frontend.

### Rumo ao MVP (usável como dependência)

- `Move all packages under com.fischer.heimdall` — pacotes genéricos (`metrics`, `health`, `utils`…) podiam colidir com classes da aplicação hospedeira; o prefixo também passou a excluir os beans do Heimdall do grafo.
- `Make heimdall-core a well-behaved library` — sem Camel/OpenAPI, sem `application.properties` no jar, `heimdall-docker` com `beans.xml` próprio e sem dependência do core nele.
- `Package the dashboard inside the heimdall-core jar` — `frontend-maven-plugin` + `base: '/heimdall/'`; o jar agora serve a UI em `/heimdall/`.
- `Add heimdall-demo` — app de exemplo que usa o Heimdall como dependência; vira o ambiente de desenvolvimento.

### Primeira release — `v0.1.0`

- Build, testes e demo validados de ponta a ponta. Rodando de verdade, apareceram e foram corrigidos dois bugs: requests com exceção não tratada (500) não geravam trace, e `/heimdall` sem barra dava 404.
- `.github/workflows/release.yml`: uma tag `vX.Y.Z` publica no GitHub Packages e cria um GitHub Release com os jars. A `v0.1.0` foi publicada em 27/10/2026.
- `TESTING.md`: guia de build, demo, uso em outro projeto e publicação.

### Tracing (revisão anterior)

Já feitos antes desta revisão: `tags` automáticas por parâmetro + `Heimdall.tag()`, captura de request/response body com redação de campos sensíveis, contexto de trace em Vert.x context locals.

---

## 2. Pendências de validação

- **Nomes das métricas Agroal** (`agroal.active.count`, `agroal.available.count`…) foram escritos de memória — conferir em `/q/metrics` numa app com datasource e `quarkus.datasource.metrics.enabled=true`.
- **Type check do frontend no CI está não bloqueante** (`continue-on-error`). Quando `npx tsc --noEmit` passar, remover a flag.
- **`npm run lint` não verifica TypeScript:** o script usa `--ext js,jsx`, então nenhum `.ts/.tsx` é analisado. Trocar para `--ext ts,tsx` (e corrigir o que aparecer) antes de colocar lint no CI.
- **Heap dump com erro** (409/501) é baixado pelo navegador como `.hprof` contendo o JSON de erro — melhorar com uma checagem antes do download.

---

## 3. Próximas etapas (por prioridade)

### P0 — Manutenção rápida dos workflows
A release `v0.1.0` gerou avisos de depreciação no GitHub Actions. Ainda funciona, mas atualizar evita quebra futura:
- `actions/checkout@v4` → `@v5` e `actions/setup-java@v4` → `@v5` em `ci.yml` e `release.yml`; `actions/setup-node@v4` → `@v5` em `ci.yml`. As v4 rodam em Node 20, que está depreciado nos runners.
- `ubuntu-latest` vai migrar para Ubuntu 26: acompanhar o primeiro build depois da troca.

### P1 — Fase 2: estatísticas por método (`@HeimdallMonitor`)
O interceptor já existe e `BeanStats` está criado mas vazio.
- `MethodStatsRegistry`: chamadas, erros, tempo total/mín/máx/médio e última chamada por `classe#método` (contadores atômicos, sem lock).
- **Coletar sempre**, não só com trace HTTP ativo — hoje o interceptor sai cedo sem trace, então chamadas do scheduler e de mensageria ficam de fora.
- Endpoint `/heimdall/api/beans/stats`; nós do grafo coloridos por saúde (verde / âmbar se lento / vermelho com erros); tabela por método no painel do bean.
- Limiar de "lento" configurável (`heimdall.monitor.slow-threshold`).

### P2 — Fase 4: alertas
- `AlertService` avaliando cada snapshot do `MetricsCollector`: heap %, CPU %, threads bloqueadas, uso de pool (Agroal/Hikari), lag do Kafka, deadlocks.
- Limiares em `application.properties` (`heimdall.alert.*`), com histerese/duração mínima para não piscar.
- `/heimdall/api/alerts` (ativos + recentes), push pelo WebSocket existente, badge no cabeçalho, notificações do navegador opcionais.

### P3 — Tempo real unificado e estados de UI
- Estender o WebSocket para alertas e novos traces; remover a mistura polling/WebSocket entre páginas.
- Banner global de "desconectado" alimentado pelo estado de conexão.
- Componentes compartilhados `LoadingState` / `EmptyState` / `ErrorState`.
- `:focus-visible` nos botões e cards (navegação por teclado).

### P4 — Tracing mais profundo
- Spans de SQL (via Agroal/JDBC ou `StatementInspector` do Hibernate) — mostrar qual query pesou dentro do span do repository.
- Propagar `traceparent` em chamadas HTTP de saída (REST Client).
- Amostragem configurável (`heimdall.tracing.sample-rate`) e limite de tamanho do registro em bytes, não só em quantidade.
- Rastrear classes fora da convenção de nome (hoje só `*Resource/*Service/*Repository/*Dao` ou pacotes configurados).

### P5 — Segurança mínima (bloqueador para sair do ambiente local)
- Autenticação opcional em `/heimdall/*` (ex.: `heimdall.auth.token` ou integração com `quarkus-security`).
- CORS configurável (hoje fixo em `localhost:5173`).
- Flags para desligar recursos sensíveis: heap dump, captura de bodies, cookies/headers.
- Não expor `e.getMessage()` cru nas respostas de erro fora de dev.

### P6 — Fase 7: extensão Quarkus
Pré-requisitos já resolvidos: prefixo das rotas, módulo Docker opcional, pacotes `com.fischer.heimdall`, UI empacotada no jar e core sem configuração própria.
- Separar `runtime` / `deployment`; registrar beans e rotas pela extensão.
- Publicar no Maven Central (`io.heimdall:heimdall-quarkus`).

### P7 — Longo prazo
Kubernetes (`heimdall-kubernetes`), Spring Boot starter, monitoramento multi-instância, `heimdall-standalone` (JMX/Actuator remoto), suporte a GraalVM native, tema claro.

---

## 4. Débitos menores encontrados na revisão

- `RabbitMQConnection.parseOverview` coloca o JSON inteiro (`rawJson`) nos stats exibidos na UI.
- Memória dos containers Docker inclui page cache (o `docker stats` desconta `inactive_file`).
- `quarkus.redis.port` não é uma propriedade real do Quarkus (`RedisConnection`).
- `MetricsService.getThreadInfo` chama `getThreadInfo(id)` uma vez por thread — usar a versão em lote.
- `HealthResource` tem a versão `"1.0.0"` fixa no código.
- Mais testes: detectores Kafka/Redis/RabbitMQ e testes de frontend (Vitest) para utilitários como `diffDumps` e `toCsv`.

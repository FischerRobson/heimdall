# Ideias de melhoria — Heimdall

Levantamento feito lendo o código atual (backend Quarkus + frontend React/Vite) e o README/roadmap já existente. Itens organizados por prioridade/categoria, com o problema concreto encontrado no código quando aplicável.

---

## 1. Tracing — estava completo? Não. Os 3 primeiros gaps já foram corrigidos.

Hoje o tracing já cobre um waterfall básico e útil: método chamado, camada (resource/service/repository), duração, erro, headers de request/response, cookies e query params. Comparado a uma ferramenta tipo Honeycomb — cujo valor central é ter **muito detalhe por span** (atributos ricos, alta cardinalidade, nada de contexto perdido em código assíncrono) — faltavam pedaços importantes. Os três primeiros abaixo já foram implementados nesta sessão, na ordem priorizada:

1. ~~`tags` do span nunca é preenchido.~~ **Feito.** `HeimdallInterceptor` agora popula `tags` automaticamente em todo span: um tag por parâmetro do método (nome real, via reflection — o projeto já compila com `-parameters`), com o valor de verdade pra tipos simples (String/número/boolean/enum) e só o nome da classe pra qualquer objeto complexo (pra nunca vazar um grafo de objeto inteiro sem querer), mais um tag `returnType` com a classe do retorno. Além disso, agora existe `tracing.Heimdall.tag(String key, Object value)` — uma API estática que o código da aplicação pode chamar de dentro de qualquer método rastreado pra anexar contexto de negócio de verdade (`Heimdall.tag("orderId", id)`), sem precisar injetar nada. É no-op seguro se chamado fora de um span ativo.
2. ~~`requestBody`/`responseBody` nunca eram preenchidos.~~ **Feito.** `TraceFilter` agora lê o corpo da request (só pra content-types textuais — JSON/texto/XML/form — reposicionando o stream depois pra não quebrar a leitura normal do endpoint) e serializa a entity da response via Jackson. Os dois passam por um redactor de campos sensíveis por nome (`password`, `secret`, `token`, `apikey`, `authorization`, etc. — mesma ideia do mask que já existia pra headers/cookies) e são truncados em 4000 caracteres pra não estourar a memória do `TraceRegistry`. A aba "response" do `TracePage.tsx` agora mostra o body também (antes só tinha headers).
3. ~~Contexto de trace era `ThreadLocal` — quebrava em qualquer salto de thread.~~ **Feito.** `TraceContext` agora guarda o trace/span-stack como *duplicated-context locals* do Vert.x (`Context.putLocal`/`getLocal`), com fallback pra `ThreadLocal` quando não existe contexto Vert.x ativo (ex.: testes). Quarkus garante um contexto duplicado por request HTTP, e pipelines Mutiny propagam esse contexto através de `.emitOn()`/continuations — então se algum método rastreado passar a devolver `Uni`/`Multi` no futuro, o span não vai mais sumir silenciosamente da árvore. Hoje o projeto não usa nada reativo (`Uni`/`Multi`/`CompletableFuture`) então isso não mudava nenhum comportamento observável agora — é uma correção pra quando esse código aparecer.

Testado de ponta a ponta com o backend rodando: `POST /metrics/gc` com `{"reason":"manual test","password":"supersecret123"}` no body gerou um trace com `requestBody` mostrando `"password":"***"` e `responseBody` com o JSON completo do resultado; os spans do waterfall vieram com `tags.returnType` preenchido automaticamente.

O que ainda falta (não fazia parte do pedido desta rodada):

4. **Só rastreia o que bate a convenção de nome, ou o que está na lista de pacotes configurada.** `HeimdallInterceptor.shouldTrace()` só intercepta classes que terminam em `Resource`/`Controller`/`Service`/`Repository`/`Dao`, ou que estejam em `heimdall.tracing.packages`. Razoável como filtro de ruído, mas qualquer lógica de negócio fora desse padrão fica invisível.
5. **Sem spans no nível de query/SQL.** HikariCP já é monitorado como pool, mas dentro de um trace não dá pra ver qual query específica pesou — só o tempo do método do Repository/Dao inteiro.
6. **Nenhuma amostragem (sampling).** Toda request vira um trace completo, sempre, guardado só em memória e só os últimos 100 (`TraceRegistry`, sem persistência).
7. **Só rastreia dentro do próprio processo** — não propaga trace ID via header (`traceparent` ou equivalente) em chamadas HTTP de saída. Não é bug, é escopo atual, mas se um dia quiser correlacionar chamadas entre serviços, precisa disso.

---

## 2. Bugs encontrados no código atual

- **WebSocket de métricas só notifica uma conexão por vez.** Em `MetricsBroadcaster.broadcast()`:
  ```java
  openConnections.findByEndpointId(MetricsSocket.ENDPOINT_ID).stream()
          .findAny()
          .ifPresent(connection -> connection.broadcast().sendTextAndAwait(snapshot));
  ```
  `findAny()` pega só uma conexão aberta. Se duas abas/usuários tiverem o dashboard aberto ao mesmo tempo, só uma recebe updates ao vivo — a outra fica travada no último snapshot. Deveria iterar (`forEach`) em todas as conexões do endpoint.
- **Gráfico de GC no dashboard quebrado** (já corrigido nesta sessão): `MetricsChart.tsx` chamava `.reduce`/`.map` direto em `garbageCollectorInfo`, mas o tipo real é `{ garbageCollectorList: GarbageCollector[] }`. Faltava o `.garbageCollectorList`.
- **`DockerPage` não atualiza sozinha.** O polling está comentado (`// const interval = setInterval(fetchContainers, 5000)`), diferente de `ConnectionsPage` e `TracePage`, que já fazem polling. Hoje só é possível ver dados atualizados saindo e voltando pra aba.
- **`TraceResource./traces/method/{method}` declara path param mas lê query param.** O `@Path("/method/{method}")` sugere `GET /traces/method/GET`, mas o método está anotado com `@QueryParam("method")`, não `@PathParam`. Na prática o segmento `{method}` da URL é ignorado e o filtro só funciona via `?method=GET`.

---

## 3. Qualidade / manutenção

- **Zero testes no projeto** (backend e frontend). Nenhum arquivo de teste em nenhum módulo. Dado que já existe lógica não trivial (detecção de conexões via JMX, parsing de bean graph via CDI, o próprio interceptor de tracing), vale começar pelos detectores (`HikariCPConnection`, `KafkaConnection`, etc.) e pelo `BeanService`, que são os pontos mais fáceis de quebrar silenciosamente.
- **Sem CI.** Não há `.github/workflows` nem pipeline equivalente — nada roda build/testes automaticamente em PRs.
- **Bundle do frontend grande e sem code-splitting.** O build do Vite já avisa: `dist/assets/index.js 708.70 kB`. `@xyflow/react` e `recharts` provavelmente respondem pela maior parte disso. Fácil ganho: lazy-load das páginas (`BeansPage`, `TracePage`, `DockerPage`) via `React.lazy`, já que o usuário normalmente só olha uma aba por vez.

---

## 4. Consistência de UX

- **Padrão de auto-refresh inconsistente entre páginas:** Dashboard usa WebSocket (tempo real), Connections e Traces usam polling (3–5s), Beans e Docker não atualizam sozinhas. Vale decidir um padrão único — idealmente estender o WebSocket que já existe pra métricas e usar o mesmo mecanismo pras outras páginas, em vez de manter dois modelos diferentes.
- **Erros de fetch são só `console.error`.** Se o backend cair ou uma chamada falhar, o usuário não vê nada na tela — só um card vazio ou o loader infinito do Bifrost. Um banner simples de "desconectado do backend" ajudaria bastante, já que o `DashboardPage` já tem lógica de reconexão de WebSocket que poderia alimentar esse estado.
- **Sem estado de erro/loading padronizado** entre páginas — cada uma reimplementa o próprio "carregando..." e "nenhum dado" com estilos ligeiramente diferentes. Poderia virar um componente compartilhado (`EmptyState`, `LoadingState`).

---

## 5. Funcionalidades (além do roadmap já existente no README)

O README já lista um roadmap bom (interceptor `@HeimdallMonitor` por método, alerting, Kubernetes, extensão Quarkus oficial, Spring Boot starter). Complementos que não estão lá:

- **Exportar thread dump / trace como arquivo** (JSON ou texto) direto da UI — hoje só dá pra ver na tela.
- **Histórico de thread dumps**, não só o último — útil pra comparar antes/depois de um problema de performance.
- **Botão de "copiar"** nas stack traces e nos valores mascarados de header/cookie, pra debug rápido sem precisar selecionar texto manualmente.
- **Indicador visual de app-host no header** — hoje o dashboard não deixa claro qual aplicação/instância está sendo observada quando várias apps usam Heimdall (nome do app, ambiente). Fica mais relevante ainda se um dia entrar o "multi-instance monitoring" que já está no roadmap de longo prazo do README.

---

## 6. Frontend polish (feito nesta sessão)

- Cursor `pointer` em todos os botões (regra global em `index.css`) e efeito de "clarear" no hover (overlay branco translúcido, em vez de escurecer) aplicado em nav tabs, cards clicáveis, filtros e o grafo de beans.
- Ainda dá pra avaliar: estados de foco visíveis via teclado (`:focus-visible`) nos botões e cards clicáveis — hoje o hover funciona bem no mouse, mas navegação por teclado não tem nenhum destaque.

---

## 7. Segurança — não é prioridade agora

Como o Heimdall hoje serve só ambiente de desenvolvimento local, os pontos abaixo **não são urgentes**. Ficam registrados pra quando o projeto mirar em rodar fora desse contexto (staging compartilhado, produção, etc.) — nesse momento eles passam a ser bloqueadores, não "nice to have":

- Nenhuma autenticação em `/heimdall/api/*` nem na UI.
- Traces expõem `Authorization`, cookies de sessão e API keys (truncados, mas parcialmente visíveis) — combinado com a falta de auth, seria uma forma fácil de vazar credenciais fora de ambiente local.
- CORS hardcoded pra `localhost:5173` no `application.properties`, em vez de configurável.

---

**Por onde eu continuaria:** com os itens 1–3 de tracing resolvidos, o próximo com melhor custo/benefício é o bug do WebSocket (seção 2) — rápido de corrigir e com impacto direto em qualquer sessão com mais de uma aba aberta. Depois disso, dos itens de tracing que sobraram, spans de query/SQL (item 5) é o que dá mais profundidade real por esforço.

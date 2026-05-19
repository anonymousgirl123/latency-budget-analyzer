# ⚡ Latency Budget Analyzer

An AI-powered IntelliJ IDEA plugin that analyzes your Java/Kotlin methods for latency hotspots — detecting HTTP calls, database queries, Kafka publishes, Redis operations, and blocking patterns. Uses Claude AI to suggest concrete, actionable optimizations.

Optionally connects to **any HTTP metrics backend** (companion Go microservice, Prometheus, Datadog, Grafana, or your own APM) to replace static estimates with real measured p99 values from your production or staging traces.

---

# 📋 Table of Contents

- [Features](#-features)
- [Prerequisites](#-prerequisites)
- [Installation](#-installation)
- [Configuration](#-configuration)
  - [AI Integration](#ai-integration)
  - [Latency Baselines](#latency-baselines)
  - [Metrics Backend – Connection](#metrics-backend--connection)
  - [Metrics Backend – Endpoint Template](#metrics-backend--endpoint-template)
  - [Metrics Backend – Authentication](#metrics-backend--authentication)
  - [Metrics Backend – Response Field Mappings](#metrics-backend--response-field-mappings)
- [How to Use](#-how-to-use)
- [Understanding the Results](#-understanding-the-results)
- [Metrics Backend Examples](#-metrics-backend-examples)
- [Optional: Latency Intelligence Platform](#-optional-latency-intelligence-platform-go-microservice)
- [Troubleshooting](#-troubleshooting)
- [Architecture](#-architecture)
- [License](#-license)
- [Author](#-author)

---

# ✨ Features

- **Static call chain analysis** — detects HTTP, DB, Kafka, Redis, and `Thread.sleep` calls using PSI/UAST (no runtime needed)
- **Latency estimates** — per-call min/max/p99 with configurable baselines
- **Hotspot detection** — highlights calls consuming >20% of the total p99 budget
- **Blocking call detection** — flags synchronous operations that should be async
- **AI-powered suggestions** — Claude API provides specific, actionable fixes with real data context
- **Config-driven metrics backend** — connect to any HTTP endpoint (Go microservice, Prometheus, Datadog, Grafana, custom APM) with zero code changes
- **Flexible authentication** — none, Bearer token, or custom API key header
- **Custom response mapping** — map any JSON shape to p99/p50/sample-count using dot-notation paths

---

# 🔧 Prerequisites

| Requirement | Version |
|---|---|
| IntelliJ IDEA | 2023.3 or later |
| Java | 17 or later |
| Claude API key | [Get one free at console.anthropic.com](https://console.anthropic.com) |

---

# 📦 Installation

## Option 1 — JetBrains Marketplace (recommended)

1. Open IntelliJ IDEA
2. Go to **Settings → Plugins → Marketplace**
3. Search for **"Latency Budget Analyzer"**
4. Click **Install** → Restart IDE

---

## Option 2 — Install from ZIP

1. Download the latest `latency-budget-analyzer-*.zip` from Releases
2. Go to **Settings → Plugins → ⚙ → Install Plugin from Disk**
3. Select the downloaded ZIP file
4. Restart IDE

---

## Option 3 — Build from source

```bash
git clone https://github.com/anonymousgirl123/latency-analyzer-plugin
cd latency-analyzer-plugin
./gradlew buildPlugin

# Output:
# build/distributions/latency-budget-analyzer-*.zip
```

Install the ZIP via Option 2.

---

# ⚙ Configuration

Open via:

```text
Settings → Tools → Latency Budget Analyzer
```

The settings page is divided into six sections:

```text
1. AI Integration
2. Latency Baselines
3. Metrics Backend – Connection
4. Metrics Backend – Endpoint Template
5. Metrics Backend – Authentication
6. Metrics Backend – Response Field Mappings
```

---

## AI Integration

```text
AI Integration

Claude API Key:    sk-ant-************
Claude Model:      claude-sonnet-4-6
Target P99 SLA:    150
```

| Field | Description |
|---|---|
| **Claude API Key** | From https://console.anthropic.com — stored locally, never shared |
| **Claude Model** | `claude-sonnet-4-6` (default) |
| **Target P99 SLA (ms)** | Your SLA budget — e.g. `150` for a 150ms p99 target |

> 💡 Your API key is stored locally in IntelliJ’s settings storage — it is never sent anywhere except the Anthropic API.

---

## Latency Baselines

```text
Latency Baselines (calibrate for your infrastructure)

HTTP:    min 20   max 500   p99 2000
DB:      min 5    max 200   p99 1000
Kafka:   min 1    max 50    p99 200
Redis:   min 1    max 10    p99 50
```

These are **static fallback estimates** used when no metrics backend is connected, or for call types the backend has no data for.

Tune them to match your infrastructure.

| Call Type | Default Min | Default Max | Default P99 |
|---|---|---|---|
| HTTP | 20ms | 500ms | 2000ms |
| Database | 5ms | 200ms | 1000ms |
| Kafka | 1ms | 50ms | 200ms |
| Redis | 1ms | 10ms | 50ms |

---

## Metrics Backend – Connection

```text
Metrics Backend – Connection

Enable real p99 from metrics backend

Base URL:      http://localhost:8080
Service Name:  order-svc
Environment:   staging
```

| Field | Description |
|---|---|
| **Enable checkbox** | Master toggle — uncheck to use static estimates only |
| **Base URL** | Root URL of your metrics service |
| **Service Name** | Injected as `{service}` in the URL template |
| **Environment** | `prod`, `staging`, or `dev` — injected as `{environment}` |

---

## Metrics Backend – Endpoint Template

```text
Metrics Backend – Endpoint Template

Placeholders:
{baseUrl} {method} {service} {environment}

URL Template:
{baseUrl}/calibrate?method={method}
&service={service}&environment={environment}
&window=24h
```

The plugin builds the request URL by substituting placeholders at runtime.

| Placeholder | Replaced with |
|---|---|
| `{baseUrl}` | Base URL field value |
| `{method}` | Detected method name (URL-encoded) |
| `{service}` | Service Name field value |
| `{environment}` | Environment field value |

### Examples

#### Companion Go microservice

```text
{baseUrl}/calibrate?method={method}&service={service}&environment={environment}&window=24h
```

#### Prometheus

```text
{baseUrl}/api/v1/query?query=histogram_quantile(0.99,rate(http_request_duration_seconds_bucket{job="{service}"}[24h]))
```

#### Datadog

```text
{baseUrl}/api/v1/query?query=avg:trace.servlet.request.duration{service:{service},env:{environment}}&from=now-1d&to=now
```

#### Grafana / custom internal APM

```text
{baseUrl}/api/latency/{service}/{method}?env={environment}&percentile=p99
```

---

## Metrics Backend – Authentication

```text
Metrics Backend – Authentication

Auth Type:       none | bearer | apikey
API Key Header:  Authorization
Token / API Key: **************
```

| Auth Type | When to use | HTTP header sent |
|---|---|---|
| `none` | Public endpoint or internal network | *(nothing added)* |
| `bearer` | JWT / OAuth token | `Authorization: Bearer <token>` |
| `apikey` | Datadog, New Relic, Elastic | `<header-name>: <key>` |

### Setup examples

#### Datadog

```text
Auth Type:       apikey
API Key Header:  DD-API-KEY
Token/API Key:   abc123yourdatadogkey
```

#### Internal APM with OAuth token

```text
Auth Type:       bearer
Token/API Key:   eyJhbGciOiJSUzI1NiJ9...
```

#### Grafana with API key

```text
Auth Type:       apikey
API Key Header:  X-Api-Key
Token/API Key:   glsa_yourgrafanaapikey
```

#### New Relic

```text
Auth Type:       apikey
API Key Header:  X-Api-Key
Token/API Key:   NRAK-XXXXXXXXXXXX
```

---

## Metrics Backend – Response Field Mappings

```text
Metrics Backend – Response Field Mappings

JSON paths to p99/p50/sample data
(dot notation: data.p99_ms)

P99 field path:          p99_ms
P50 field path:          p50_ms
Sample count field path: sample_count
```

Tell the plugin which fields in your backend JSON response contain the latency values.

Supports **dot notation** for nested objects.

### Flat response

```json
{
  "p50_ms": 12.4,
  "p99_ms": 87.3,
  "sample_count": 4200
}
```

Paths:

```text
p50_ms
p99_ms
sample_count
```

### Nested response

```json
{
  "data": {
    "p50_ms": 12.4,
    "p99_ms": 87.3
  },
  "meta": {
    "count": 4200
  }
}
```

Paths:

```text
data.p50_ms
data.p99_ms
meta.count
```

### Custom APM shape

```json
{
  "metrics": {
    "latency": {
      "p99": 87.3,
      "p50": 12.4
    }
  },
  "samples": 4200
}
```

Paths:

```text
metrics.latency.p99
metrics.latency.p50
samples
```

> ⚠ Array indexing (e.g. `result.0.value`) is not yet supported. For Prometheus or Datadog responses that return arrays, wrap the query in a thin adapter service that returns a flat JSON object.

---

# 🚀 How to Use

## Analyze a method

1. Open any **Java or Kotlin** file
2. Click inside a method body
3. **Right-click → Analyze Latency Budget**

Or use the keyboard shortcut:

```text
Shift + Ctrl + Alt + L
```

4. A progress bar shows the analysis stages:

```text
[███████ ] Scanning call chain...              (instant)
[███████ ] Fetching real p99 from backend...  (1–2s if enabled)
[███████ ] Estimating latency...              (instant)
[███████ ] Fetching AI suggestions...         (3–10s)
```

5. The **Latency Analyzer** tool window opens with full results.

---

## Example — what gets detected

```java
public Order placeOrder(String userId, String productId) {

    // ← HTTP call detected
    String user = restTemplate.getForObject(
        "https://identity-svc/users/" + userId,
        String.class
    );

    // ← DB write detected
    Order order = orderRepository.save(new Order(userId, productId));

    // ← Redis write detected
    redisTemplate.opsForValue().set("order:" + order.getId(), order);

    // ← Kafka publish detected
    kafkaTemplate.send("order-events", order.getId(), "PLACED");

    return order;
}
```

---

# 📊 Understanding the Results

## Summary panel

```text
✅ placeOrder | 4 calls | p99 = 3250ms | ⚠ 2 hotspot(s)

Method: placeOrder      Calls: 4
Min:    27 ms           Max:   760 ms
P99:    3250 ms         SLA:   ⚠ 2 hotspot(s)
```

| Field | Description |
|---|---|
| **Method** | Name of the analyzed method |
| **Calls** | Total number of outbound calls detected |
| **Min** | Best-case total latency (all calls fast path) |
| **Max** | Worst-case total latency |
| **P99** | 99th percentile estimate — use this for SLA calculation |
| **SLA** | ✅ OK if no hotspots • ⚠ N hotspot(s) if any call exceeds 20% of p99 budget |

---

## Call chain table

| Column | Description |
|---|---|
| Icon | 🌐 HTTP • 🗄 DB • 📩 Kafka • ⚡ Redis • 💤 Sleep |
| Type | Call category |
| Operation | Method name and truncated code snippet |
| Line | Source line number |
| Min ms | Optimistic latency estimate |
| Max ms | Pessimistic latency estimate |
| Est P99 | Static p99 estimate based on baselines |
| **Real P99** | Actual measured p99 from your backend — shows `-` if unavailable |
| 🔒 / 🟢 / 🔥 | 🔴 = blocking thread • 🟢 = non-blocking • 🔥 = hotspot |

---

## Row colours

| Colour | Condition | Meaning |
|---|---|---|
| 🔴 Red background | Est P99 ≥ 1000ms | Critical latency risk |
| 🟡 Yellow background | Est P99 ≥ 200ms | Worth optimizing |
| No colour | Est P99 < 200ms | Acceptable |

---

## AI Suggestions panel

Claude provides four structured sections:

```text
🔴 CRITICAL ISSUES
Blocking calls on latency-sensitive threads, N+1 query risks, synchronous waits

🟡 OPTIMIZATION OPPORTUNITIES
Caching strategies, parallel execution (CompletableFuture/reactive), batching

🟢 QUICK WINS
Low-effort, high-impact changes (e.g. @Cacheable, fire-and-forget Kafka)

💡 RECOMMENDED FIX (code snippet)
A concrete Java/Kotlin snippet for the single most impactful change
```

When real p99 data is available, Claude prioritizes it over static estimates and explicitly references actual measured values.

---

# 🔗 Metrics Backend Examples

## Companion Go microservice (default – zero config)

```text
Enable:            ✅
Base URL:          http://localhost:8080
Service Name:      order-svc
Environment:       staging
URL Template:      {baseUrl}/calibrate?method={method}&service={service}&environment={environment}&window=24h
Auth Type:         none
P99 field path:    p99_ms
P50 field path:    p50_ms
Sample count path: sample_count
```

Expected response:

```json
{
  "p50_ms": 12.4,
  "p99_ms": 87.3,
  "sample_count": 4200
}
```

---

## Prometheus

```text
Enable:         ✅
Base URL:       http://prometheus.internal:9090
Service Name:   order-svc
Environment:    staging
URL Template:   {baseUrl}/api/v1/query?query=histogram_quantile(0.99,rate(http_request_duration_seconds_bucket{job="{service}"}[24h]))
Auth Type:      none (or bearer if behind an auth proxy)
P99 field path: data.result.0.value.1
```

> 💡 Prometheus returns values in seconds. Write a thin adapter service to multiply by 1000 and return a flat JSON object for best results.

---

## Datadog

```text
Enable:          ✅
Base URL:        https://api.datadoghq.com
Service Name:    order-svc
Environment:     prod
URL Template:    {baseUrl}/api/v1/query?query=avg:trace.servlet.request.duration{service:{service},env:{environment}}&from=now-1d&to=now
Auth Type:       apikey
API Key Header:  DD-API-KEY
Token/API Key:   your-datadog-api-key
P99 field path:  series.0.pointlist.0.1
```

---

## Grafana / internal APM with Bearer token

```text
Enable:            ✅
Base URL:          https://apm.corp.com
Service Name:      order-svc
Environment:       staging
URL Template:      {baseUrl}/api/v2/latency?method={method}&svc={service}&env={environment}
Auth Type:         bearer
Token/API Key:     eyJhbGciOiJSUzI1NiJ9...
P99 field path:    metrics.p99_ms
P50 field path:    metrics.p50_ms
Sample count path: metadata.sample_count
```

---

# 🛰 Optional: Latency Intelligence Platform (Go microservice)

The companion Go microservice is the **easiest way to get real p99 data** if you are starting fresh. It ingests OpenTelemetry traces from your Spring Boot app and exposes them to the plugin automatically.

## What it does

- Receives traces from your Spring Boot app via OpenTelemetry
- Computes real p50/p95/p99 per method from production/staging traffic
- Serves these to the plugin — replacing guesses with ground truth
- Detects latency regressions between commits (for CI pipelines)

---

## Setup — 3 steps

### Step 1 — Start the Latency Intelligence Platform

```bash
git clone https://github.com/anonymousgirl123/latency-intelligence
cd latency-intelligence/docker

podman compose up --build
# or:
docker compose up --build
```

Services started:

- Go microservice → `http://localhost:8080`
- ClickHouse (trace storage) → `localhost:9000`
- Redis (cache) → `localhost:6379`
- OTel Collector → `localhost:4318` (HTTP) / `localhost:4317` (gRPC)

---

### Step 2 — Instrument your Spring Boot app

Add to `application.yml`:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0

otlp:
  tracing:
    endpoint: http://localhost:4318/v1/traces

otel:
  resource:
    attributes:
      deployment.environment: staging
      git.commit.sha: ${GIT_COMMIT_SHA:local}
      service.name: your-service-name
```

Add to `pom.xml`:

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
    <version>1.38.0</version>
</dependency>
```

---

### Step 3 — Enable in plugin settings

Use the **default template** — no changes needed beyond connection fields:

| Field | Value |
|---|---|
| ✅ Enable | Check this box |
| Base URL | `http://localhost:8080` |
| Service Name | Your app's service name (e.g. `order-svc`) |
| Environment | `staging` or `prod` |
| URL Template | *(leave as default)* |
| Auth Type | `none` |
| Response fields | *(leave as default)* |

---

## Generate trace data

Send some traffic to your app (manual or load test), then analyze a method — the **Real P99** column will populate with actual measurements.

```bash
# Quick load test
for i in {1..50}; do
  curl -s -X POST http://localhost:8081/orders \
    -H "Content-Type: application/json" \
    -d '{"userId":"u1","productId":"p1"}' > /dev/null
done
```

---

# 🔧 Troubleshooting

## "No PSI file found" / action not appearing

- Make sure the file is **Java or Kotlin** (`.java` or `.kt`)
- The project must have the **Java plugin** enabled (IntelliJ IDEA, not other IDEs)
- Try reopening the file

---

## AI suggestions show "No API key configured"

- Go to **Settings → Tools → Latency Budget Analyzer**
- Paste your Claude API key from https://console.anthropic.com

---

## AI call failed / network error

- Check your internet connection
- If on a corporate network: configure your HTTP proxy at:

```text
Settings → Appearance & Behavior → System Settings → HTTP Proxy
```

- IntelliJ proxy settings are picked up automatically

---

## Real P99 shows "-" for all rows

- Confirm **Enable** is checked in settings
- Confirm the backend is reachable:

```bash
curl http://localhost:8080/health
```

- Confirm the **Service Name** matches exactly (case-sensitive)
- For the Go microservice: ensure at least 30 trace samples have been collected
- Check **Response Field Mappings** — field paths must match your backend JSON exactly

---

## Auth 401 / 403 from backend

- Double-check **Auth Type**, **API Key Header name**, and **Token/API Key** value
- Ensure there are no leading/trailing spaces in the token field

---

## URL template not resolving correctly

- Ensure `{baseUrl}` is present in the template and **Base URL** is filled in
- Check for typos in placeholder names — they are case-sensitive:

```text
{method} not {Method}
```

---

## Wrong p99 value appears (or `null`)

- Open your browser / curl and inspect the raw JSON response from your backend
- Update **P99 field path** to match the exact key  
  (use dot notation for nested paths: `data.p99_ms`)

---

# 🏗 Architecture

## Analysis Pipeline

```text
1. CodeAnalyzer
   PSI/UAST scan → detect HTTP/DB/Kafka/Redis calls
   Build LatencyNode with baseline estimates from settings

2. CalibrateService
   HTTP GET → configured metrics backend URL
   Enrich each node with real p99/p50 if available
   Degrades silently if backend is unreachable

3. LatencyEstimator
   Sum sequential (blocking) calls
   Max of parallel (non-blocking) calls
   Identify hotspots ≥ 20% of p99 budget

4. AIService
   Build prompt including real + estimated data
   Call Claude API → return 4-section suggestions

5. LatencyResultPanel
   Render results in IntelliJ tool window
```

---

## Plugin Components

```text
IntelliJ IDEA
└── Latency Budget Analyzer Plugin
    ├── CodeAnalyzer        — PSI/UAST static analysis
    ├── LatencyEstimator    — p50/p99 estimation from baselines
    ├── CalibrateService    — config-driven real p99 fetch
    │   ├── URL template    — any endpoint, any path/query params
    │   ├── Auth            — none | bearer | apikey header
    │   └── Field mapping   — dot-notated JSON path extraction
    ├── AIService           — calls Claude API with enriched data
    └── LatencyResultPanel  — renders results in tool window
```

---

## Supported Metrics Backends

```text
Plugin  <── GET /calibrate?method=… ──>  Any HTTP endpoint returning JSON

Examples:
- Companion Go microservice (localhost:8080)
- Prometheus               (/api/v1/query)
- Datadog                  (api.datadoghq.com)
- Grafana                  (any datasource API)
- Your own internal APM
```

---

# 📄 License

Apache 2.0 — see [LICENSE](LICENSE)

---

# 👤 Author

**Kamini Kamal**

- Email: kamini@tech-s.org
- Website: https://www.kamini-kamal.com


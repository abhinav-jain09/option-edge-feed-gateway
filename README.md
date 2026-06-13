# OptionsEdge Feed Gateway

Standalone Kafka-to-WebSocket fanout service for OptionsEdge display feeds.

Created at:

```text
/Users/abhinav/development/workspace/options-edge-feed-gateway
```

This app does not connect to IBKR and does not place orders. It consumes final display topics from Kafka, keeps an in-memory latest-value cache, and sends data to frontend clients over one WebSocket endpoint.

## Flow

```text
Kafka final display topics -> feed gateway in-memory cache -> /ws/events WebSocket clients
```

Use this when the UI should read from one WebSocket fanout service instead of every OptionsEdge app instance consuming Kafka and replaying data on restart.

Unusual Whales GEX is forwarded from both the current and history-enriched topics as the same UI event:

```text
options.unusualwhales.gex.strike         -> gex-by-strike
options.unusualwhales.gex.strike.history -> gex-by-strike
```

The history topic keeps the same base payload and adds `history` buckets for the strike hover tooltip.

## Local URLs

These are the local URLs and ports used by the tested setup.

| Service | URL / Address | Purpose |
| --- | --- | --- |
| OptionsEdge UI | `http://127.0.0.1:8080/` | Browser UI served by the main `options-edge` app |
| OptionsEdge config | `http://127.0.0.1:8080/api/config` | Confirms active symbol, expiry, provider, and gateway WebSocket config |
| Feed gateway root | `http://127.0.0.1:8091/` | Basic gateway endpoint |
| Feed gateway health | `http://127.0.0.1:8091/health` | Gateway running/caught-up/client/cache status |
| Feed gateway WebSocket | `ws://127.0.0.1:8091/ws/events` | Browser data stream used by OptionsEdge UI |
| IB Gateway API | `127.0.0.1:4001` | IBKR API socket used by `options-edge`; this is not an HTTP URL |
| Kafka bootstrap | `192.168.100.252:9092,192.168.100.252:9094,192.168.100.252:9096` | Kafka brokers used by producer/stream/gateway consumers |
| Schema Registry | `http://192.168.100.252:8082` | Avro Schema Registry for display topics |

Current tested OptionsEdge UI configuration:

```json
{
  "provider": "IB",
  "symbol": "SPX",
  "expiry": "20260609",
  "feedGatewayEnabled": true,
  "feedGatewayWsUrl": "ws://127.0.0.1:8091/ws/events"
}
```

With this setup, the browser loads the UI from `http://127.0.0.1:8080/` and receives live table data from the gateway WebSocket at `ws://127.0.0.1:8091/ws/events`.

## Remote Deployment Structure

If the feed gateway runs on a remote machine, the structure should stay the same logically:

```text
IB Gateway -> options-edge -> Kafka final display topics -> options-edge-feed-gateway -> browser WebSocket
```

Recommended machine split:

| Machine / Service | Runs | Needs access to |
| --- | --- | --- |
| IB/options machine | `IB Gateway` and main `options-edge` app | IBKR API, Kafka, Schema Registry |
| Kafka machine | Kafka broker and Schema Registry | Network access from `options-edge` and feed gateway |
| Gateway machine | `options-edge-feed-gateway` | Kafka and Schema Registry |
| Browser machine | OptionsEdge UI in Chrome/Safari/etc. | OptionsEdge UI URL and gateway WebSocket URL |

The gateway does not need access to IB Gateway. It only needs Kafka and Schema Registry.

### Remote URL Pattern

Replace `<gateway-host>` with the remote gateway machine IP or DNS name.

| Service | Remote URL / Address |
| --- | --- |
| OptionsEdge UI | `http://<options-edge-host>:8080/` |
| OptionsEdge config | `http://<options-edge-host>:8080/api/config` |
| Feed gateway root | `http://<gateway-host>:8091/` |
| Feed gateway health | `http://<gateway-host>:8091/health` |
| Feed gateway WebSocket | `ws://<gateway-host>:8091/ws/events` |
| Kafka bootstrap | `<kafka-host>:9092,<kafka-host>:9094,<kafka-host>:9096` |
| Schema Registry | `http://<schema-registry-host>:8082` |

Example with private LAN IPs:

```text
OptionsEdge UI:          http://192.168.100.20:8080/
OptionsEdge config:      http://192.168.100.20:8080/api/config
Feed gateway health:     http://192.168.100.50:8091/health
Feed gateway WebSocket:  ws://192.168.100.50:8091/ws/events
Kafka bootstrap:         192.168.100.252:9092,192.168.100.252:9094,192.168.100.252:9096
Schema Registry:         http://192.168.100.252:8082
```

Main `options-edge` must be started with the gateway URL that the browser can reach:

```bash
APP_FEED_GATEWAY_ENABLED=true
APP_FEED_GATEWAY_WS_URL=ws://192.168.100.50:8091/ws/events
```

Do not configure the browser-facing gateway URL as `ws://127.0.0.1:8091/ws/events` unless the browser itself is running on the same machine as the gateway. In a browser, `127.0.0.1` means the user's own computer, not the remote gateway server.

### Remote Gateway Startup

On the remote gateway machine:

```bash
APP_WEB_PORT=8091 \
KAFKA_BOOTSTRAP_SERVERS=192.168.100.252:9092,192.168.100.252:9094,192.168.100.252:9096 \
KAFKA_SCHEMA_REGISTRY_URL=http://192.168.100.252:8082 \
mvn spring-boot:run
```

Then verify from the browser/client machine:

```bash
curl http://192.168.100.50:8091/health
curl http://192.168.100.20:8080/api/config
```

The `/api/config` response from `options-edge` should contain:

```json
{
  "feedGatewayEnabled": true,
  "feedGatewayWsUrl": "ws://192.168.100.50:8091/ws/events"
}
```

### HTTPS / Public Domain Setup

If the OptionsEdge UI is served over HTTPS, the gateway WebSocket should also use secure WebSocket:

```text
wss://feed.example.com/ws/events
```

In that setup, put a reverse proxy such as Nginx, Caddy, or a load balancer in front of the gateway:

```text
Browser -> https://options.example.com/ -> options-edge
Browser -> wss://feed.example.com/ws/events -> reverse proxy -> gateway:8091
```

Then start `options-edge` with:

```bash
APP_FEED_GATEWAY_ENABLED=true
APP_FEED_GATEWAY_WS_URL=wss://feed.example.com/ws/events
```

Kafka and Schema Registry should stay private. They should be reachable by `options-edge` and `options-edge-feed-gateway`, but they should not be exposed directly to browsers.

## Startup Replay Behavior

The gateway intentionally reads compacted/current topics from the beginning on startup so it can rebuild the latest cache.

The replay cache is bounded by `GATEWAY_CACHE_TTL_MS`, which defaults to 15 minutes. Records older than the TTL, based on the Kafka record timestamp, are not kept in the gateway cache and are not sent to new WebSocket clients. This prevents old expiries from filling the UI after restart while still allowing active strikes to populate quickly when the producer keeps emitting fresh snapshots.

It does not broadcast old startup records one-by-one to connected WebSocket clients. While startup replay is in progress, live records update the gateway cache but are not sent to the UI yet. After each cache consumer catches up to the startup end offset, it sends the latest cached state once and then broadcasts only latest-state WebSocket batches.

The alert topic starts at the end, so old alerts are not replayed.

If Kafka or the network is temporarily unavailable, each gateway consumer restarts with exponential backoff. Cache consumers mark themselves not caught up while recovering, rebuild the bounded cache window, and replay the latest cached state to connected UI clients when caught up again. Live consumers replay the bounded cache window after a restart so clients do not need a browser refresh after an outage.

The gateway `/health` values `snapshots`, `gexByStrike`, and `currentStates` are cache sizes, not live message counters. They may stay the same while live records are still being sent to the UI, because updates replace the same strike keys in the in-memory cache.

## Spot Price Update Troubleshooting

Spot price updates require this full path to be live:

```text
IB Gateway -> options-edge raw publisher -> options.ibkr.raw -> options-edge RawToDisplayBridge -> display -> options-edge-feed-gateway -> browser WebSocket
```

The main `options-edge` app must produce fresh records into the final `display` topic. The Kafka Streams application ids should be stable by default so restarts reuse the same internal changelog/repartition topics instead of creating new timestamped topics.

```text
options-flow-display-streams-v7
options-flow-volume-direction-streams-v1
options-flow-volume-sandwich-streams-v2
```

Only enable session-scoped ids temporarily for emergency/debug runs, because every restart with session-scoped ids creates new Kafka Streams internal topics:

```bash
KAFKA_DISPLAY_STREAMS_SESSION_SCOPED=true
KAFKA_VOLUME_DIRECTION_STREAMS_SESSION_SCOPED=true
KAFKA_VOLUME_SANDWICH_STREAMS_SESSION_SCOPED=true
```

When the UI spot price does not update:

1. Check the UI config:

```bash
curl http://127.0.0.1:8080/api/config
```

The `symbol` and `expiry` must match the live WebSocket payloads. The frontend drops snapshot messages for a different expiry.

2. Check gateway health:

```bash
curl http://127.0.0.1:8091/health
```

`avroCaughtUp` should be `true`. Do not use the `snapshots` count as a live-update counter.

3. Check Kafka offsets on the configured broker:

```bash
/Users/abhinav/development/confluent-7.3.1/bin/kafka-get-offsets \
  --bootstrap-server 192.168.100.252:9092 \
  --topic options.ibkr.raw

/Users/abhinav/development/confluent-7.3.1/bin/kafka-get-offsets \
  --bootstrap-server 192.168.100.252:9092 \
  --topic display
```

Both raw and display offsets should increase during live market data. If raw increases but display does not, the problem is in `options-edge` `RawToDisplayBridge`, not in the gateway UI fanout.

4. Hard-refresh the browser after rebuilding `options-edge` so the latest `/option-chain.js?v=...` asset is loaded.

## Endpoints

- `GET /` returns a short text description.
- `GET /health` returns gateway state and cache counts.
- `GET /metrics` returns Prometheus text metrics for gateway readiness, cache sizes, clients, batches, and consumer restarts.
- `WS /ws/events` streams feed events to frontend clients.

Single WebSocket messages use this envelope and remain supported for compatibility:

```json
{"type":"snapshot","data":{}}
```

Latest-state feed updates are normally sent as fixed-cadence batches:

```json
{"type":"ui-batch","data":{"snapshots":[],"paces":[],"directionalPressures":[],"volumeSandwiches":[],"gexByStrike":[],"hpsfLatestSignals":[],"hpsfMarketFlows":[],"hpsfTopCandidates":[],"hpsfAudits":[],"hpsfExitIntents":[]}}
```

Current event types:

- `status`
- `ui-batch`
- `snapshot`
- `gex-by-strike`
- `pace`
- `directional-pressure`
- `volume-sandwich`
- `volume-sandwich-alert`
- `hpsf-latest-signal`
- `hpsf-market-flow`
- `hpsf-top-candidates`
- `hpsf-audit`
- `hpsf-exit-intent`

HPSF current-decision views are mapped only from `options.hpsf.latest-signal`; the gateway does not infer current decisions from historical `options.hpsf.signal`.

## Run

Requires Java 21 and Maven.

```bash
cd /Users/abhinav/development/workspace/options-edge-feed-gateway
mvn spring-boot:run
```

Default HTTP port is `8091`.

```bash
curl http://127.0.0.1:8091/health
```

WebSocket URL:

```text
ws://127.0.0.1:8091/ws/events
```

## Configuration

Environment variables and matching Java system properties are supported.

| Name | Default | Purpose |
| --- | --- | --- |
| `APP_WEB_PORT` | `8091` | HTTP and WebSocket port |
| `KAFKA_ENABLED` | `true` | Start Kafka consumers |
| `KAFKA_BOOTSTRAP_SERVERS` | `192.168.100.252:9092,192.168.100.252:9094,192.168.100.252:9096` | Kafka bootstrap servers |
| `KAFKA_SCHEMA_REGISTRY_URL` | `http://192.168.100.252:8082` | Schema Registry URL for Avro display topics |
| `GATEWAY_KAFKA_GROUP_ID` | `options-edge-feed-gateway` | Base id used for consumer group/client names |
| `KAFKA_DISPLAY_TOPIC` | `display` | Avro option-chain display topic |
| `KAFKA_UNUSUAL_WHALES_GEX_TOPIC` | `options.unusualwhales.gex.strike` | JSON strike-level Unusual Whales GEX topic |
| `KAFKA_VOLUME_DIRECTION_CURRENT_TOPIC` | `display.volume.direction.current` | JSON current volume-direction topic |
| `KAFKA_VOLUME_SANDWICH_CURRENT_TOPIC` | `display.volume.sandwich.current` | JSON current volume-sandwich topic |
| `KAFKA_VOLUME_SANDWICH_ALERTS_TOPIC` | `display.volume.sandwich.alerts` | JSON live-only sandwich-alert topic |
| `KAFKA_HPSF_LATEST_SIGNAL_TOPIC` | `options.hpsf.latest-signal` | HPSF current decision source topic |
| `KAFKA_HPSF_MARKET_FLOW_TOPIC` | `options.hpsf.market-flow` | HPSF market-flow source topic |
| `KAFKA_HPSF_STRIKE_SCORE_TOPIC` | `options.hpsf.strike-score` | HPSF strike-score source topic for top candidates |
| `KAFKA_HPSF_AUDIT_TOPIC` | `options.hpsf.audit` | HPSF audit source topic |
| `KAFKA_HPSF_EXIT_SIGNAL_TOPIC` | `options.hpsf.exit-signal` | HPSF informational exit-intent source topic |
| `GATEWAY_KAFKA_POLL_MS` | `250` | Kafka poll interval |
| `GATEWAY_WS_BATCH_MS` | `125` | WebSocket latest-state batch cadence in milliseconds |
| `GATEWAY_KAFKA_METADATA_TIMEOUT_MS` | `30000` | Topic metadata wait timeout |
| `GATEWAY_KAFKA_RETRY_INITIAL_MS` | `1000` | Initial delay before restarting a failed Kafka consumer |
| `GATEWAY_KAFKA_RETRY_MAX_MS` | `30000` | Maximum delay between Kafka consumer restart attempts |
| `GATEWAY_CACHE_TTL_MS` | `900000` | Maximum age for records kept in the gateway replay cache |

Example:

```bash
APP_WEB_PORT=8091 \
KAFKA_BOOTSTRAP_SERVERS=192.168.100.252:9092,192.168.100.252:9094,192.168.100.252:9096 \
KAFKA_SCHEMA_REGISTRY_URL=http://192.168.100.252:8082 \
mvn spring-boot:run
```

## Build Check

```bash
mvn test
```

The project currently has no test classes, so this command verifies dependency resolution and Java compilation.

## Integration Note

The `option-edge-gateway-integration` branch of the main `options-edge` app is configured to open `ws://127.0.0.1:8091/ws/events` instead of using the main app WebSocket directly. That keeps final-topic Kafka consumption in one small service and lets UI clients share the same latest-value stream.

Main app environment:

```bash
APP_FEED_GATEWAY_ENABLED=true
APP_FEED_GATEWAY_WS_URL=ws://127.0.0.1:8091/ws/events
IB_EXPIRY=20260609
```

Status checks:

```bash
curl http://127.0.0.1:8091/health
curl http://127.0.0.1:8080/api/config
```

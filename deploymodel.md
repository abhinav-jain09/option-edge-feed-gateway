# OptionsEdge Remote Deployment Model

This document describes how to deploy both apps when the feed gateway runs on a remote machine.

## Target Flow

```text
IB Gateway/TWS -> options-edge -> Kafka -> options-edge-feed-gateway -> browser
```

Responsibilities:

| Component | Responsibility |
| --- | --- |
| `IB Gateway/TWS` | Provides IBKR market data API access |
| `options-edge` | Connects to IBKR, processes option chain data, publishes final Kafka topics, serves the UI |
| `Kafka` | Stores raw/final display topics |
| `options-edge-feed-gateway` | Consumes final Kafka topics and broadcasts latest data over WebSocket |
| Browser | Loads UI from `options-edge` and receives live data from the gateway WebSocket |

## Recommended Remote Setup

Run two Java services on the remote machine:

```text
options-edge              port 8080
options-edge-feed-gateway port 8093
```

If IB Gateway also runs on the same machine, `options-edge` should connect to:

```text
127.0.0.1:4001
```

If IB Gateway runs on another private machine, use that private IP instead:

```text
IB_HOST=<ib-gateway-private-ip>
IB_PORT=4001
```

Do not expose the IB Gateway API port publicly.

## Machine Requirements

Remote machine needs:

- Java 21 or newer
- Maven, or prebuilt JAR/WAR artifacts copied to the server
- Network access to Kafka
- Network access to Schema Registry
- Network access to IB Gateway API if `options-edge` runs there

Kafka and Schema Registry should stay private. Browsers should not connect to Kafka directly.

## Service URLs

Replace `<remote-machine-ip>` with the remote server IP or DNS name.

| Service | URL |
| --- | --- |
| OptionsEdge UI | `http://<remote-machine-ip>:8080/` |
| OptionsEdge config | `http://<remote-machine-ip>:8080/api/config` |
| Feed gateway health | `http://<remote-machine-ip>:8093/health` |
| Feed gateway WebSocket | `ws://<remote-machine-ip>:8093/ws/events` |

Example:

```text
OptionsEdge UI:          http://192.168.100.20:8080/
OptionsEdge config:      http://192.168.100.20:8080/api/config
Feed gateway health:     http://192.168.100.20:8093/health
Feed gateway WebSocket:  ws://192.168.100.20:8093/ws/events
```

Important: do not use `ws://127.0.0.1:8093/ws/events` for remote browser clients. In a browser, `127.0.0.1` means the user's own computer, not the remote gateway server.

## Deploy `options-edge`

Run the main app on port `8080`.

Environment:

```bash
APP_WEB_PORT=8080
APP_PROVIDER=IB

IB_HOST=127.0.0.1
IB_PORT=4001
IB_CLIENT_ID=112
IB_SYMBOL=SPX
IB_EXPIRY=20260609
IB_TRADING_CLASS=SPXW
IB_USE_DELAYED_DATA=false
IB_MAX_STRIKES=45

APP_FEED_GATEWAY_ENABLED=true
APP_FEED_GATEWAY_WS_URL=ws://<remote-machine-ip>:8093/ws/events

KAFKA_BOOTSTRAP_SERVERS=192.168.100.252:9092
KAFKA_SCHEMA_REGISTRY_URL=http://192.168.100.252:8082
```

The key setting is:

```bash
APP_FEED_GATEWAY_WS_URL=ws://<remote-machine-ip>:8093/ws/events
```

That URL is sent to the browser through:

```text
http://<remote-machine-ip>:8080/api/config
```

## Deploy `options-edge-feed-gateway`

Run the gateway app on port `8093`.

Environment:

```bash
APP_WEB_PORT=8093
KAFKA_BOOTSTRAP_SERVERS=192.168.100.252:9092
KAFKA_SCHEMA_REGISTRY_URL=http://192.168.100.252:8082
```

The gateway does not need IBKR credentials and does not connect to IB Gateway.

## Firewall / Security Group

Open these ports to browser/client machines:

```text
8080 -> OptionsEdge UI
8093 -> Feed gateway HTTP and WebSocket
```

Keep these private:

```text
4001 -> IB Gateway API
9092 -> Kafka
8082 -> Schema Registry
```

## Validation

From the browser/client machine:

```bash
curl http://<remote-machine-ip>:8093/health
curl http://<remote-machine-ip>:8080/api/config
```

The gateway health should show:

```json
{
  "running": true,
  "avroCaughtUp": true,
  "stateCaughtUp": true
}
```

The OptionsEdge config should show:

```json
{
  "feedGatewayEnabled": true,
  "feedGatewayWsUrl": "ws://<remote-machine-ip>:8093/ws/events"
}
```

Open the UI:

```text
http://<remote-machine-ip>:8080/
```

Expected UI status:

```text
Streaming
SPX 2026-06-09 | Gateway ib feed
```

## Better Production Setup

For production, use HTTPS and a reverse proxy instead of exposing raw ports.

Recommended public URLs:

```text
https://options.example.com/
wss://feed.example.com/ws/events
```

Reverse proxy model:

```text
Browser -> https://options.example.com/ -> options-edge:8080
Browser -> wss://feed.example.com/ws/events -> options-edge-feed-gateway:8093
```

Then start `options-edge` with:

```bash
APP_FEED_GATEWAY_ENABLED=true
APP_FEED_GATEWAY_WS_URL=wss://feed.example.com/ws/events
```

If the UI is served over HTTPS, the WebSocket should use `wss://`, not `ws://`, otherwise browsers may block it as mixed content.


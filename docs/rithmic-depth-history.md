# Recorded liquidity, current depth and zone visibility

This document describes the feature-branch source behavior, not proof of deployment. Runtime evidence must come from Jenkins and live UI checks.

The Order chart uses three separate data products. Current bars and numeric quantities use every admitted gateway depth frame (normally a 250 ms display cadence). Recent browser samples and recorded gateway snapshots supply historical heatmap cells. Candles, indicators and orders use the chart's existing shared price and time transforms.

## Recorded history

The gateway has a separate read-only consumer of `options.rithmic.es-depth-spx`. It warms a bounded, compressed in-memory cache from retained Kafka data, independently of the live relay. It normally keeps one complete snapshot per second, also retaining source/status transitions, preserves each snapshot's original ES/SPX basis and timestamps, and records discontinuities as gaps. No browser connection or laptop process supplies this history.

The initial cache window is at most two hours and 7,201 columns, bounded by 64 MiB of compressed data and a 160,000-record bootstrap scan. Actual available coverage may be shorter because of retention, outages, invalid records or memory/scan bounds. Historical requests report available coverage. This is not full tick-by-tick or unlimited-session replay.

The existing authenticated gateway WebSocket accepts `rithmic-depth-history` requests with request ID, time range, price range and cursor. Replies use `rithmic-liquidity-history`, at most 32 frames and 512 KiB (and no more than a quarter of the configured socket byte budget). The browser asks for subsequent pages after processing the previous one. Requests are rate bounded and share the existing historical-request concurrency permits. Replies are not coalesced with live snapshots. History loading never advances the live sequence watermark or makes old data appear current.

The historical validator uses each frame's original emission time to verify its original source/basis freshness. It does not weaken live freshness validation. The browser independently validates historical frames, their request range, generation and prices; viewport clipping may legitimately leave one historical book side empty. Missing/malformed history is not replaced with today's book.

## Rendering and interpretation

The historical raster budget is distributed over the entire visible time range, rather than spent only on the newest columns. At wide zoom, historical cells show the maximum resting size among the retained snapshots within an adaptive time/price cell, not traded volume, summed size or proof that the order persisted for the whole cell. The toolbar and hover text identify these historical peaks. Gaps split cells; the future stays empty. The newest right-edge column and current-depth profile remain separate from aggregated historical peaks.

History is sampled at one second before browser aggregation, so orders added and removed between snapshots may not appear in historical cells. Current bars use the latest gateway frame. Neither a price crossing nor a depth reduction alone identifies execution versus cancellation or proves hidden-liquidity intent.

Follow mode advances the shared time window toward newer timestamps and keeps the newest point near the right edge. For a fixed historical timestamp this moves its X coordinate left, instead of the old midpoint recenter that jumped the picture right. Manual panning still pauses following. The initial overnight fit is bounded to 90 candles; wider manual views can exceed recorded coverage and show the coverage message.

## Zones

Only order-zone background fill and hatch rectangles are tagged for overlap handling. The renderer tests them against actually painted depth cells in the same SVG/chart coordinate space. Overlapping fills/hatching become more transparent, and the existing liquidity raster receives a bounded contrast boost inside the zone mask. Zone strokes, labels, order hit targets, candle prices, quantities and colour normalization do not change. Multiple overlapping zones use a union mask so brightness does not compound. Disabling liquidity restores normal zone fills.

## Rollout and validation

Deploy the gateway first, allow its recorded cache to warm, then deploy the web image. Old web clients ignore the new request path. The web keeps live data independent if history is unavailable, rejects obsolete history replies after reconnect/view changes, and asks for narrower price ranges when a history frame cannot fit the reply budget.

Focused tests cover old-source timestamps, pagination, price filtering, rate and byte bounds, history/live isolation, outage gaps, broad-view raster coverage, monotonic leftward movement, zone overlap and current-book removal/refill. Mock benchmarks exclude Kafka/network costs and native browser raster/font costs; those need runtime verification. Wider repository JavaScript failures must be compared with unchanged main. Actual UI screenshot verification is required before claiming rollout completion.

## Source anchors

- `option-edge-feed-gateway/src/main/java/app/feedgateway/RithmicDepthHistory.java:25` — `static final long WINDOW`.
- `option-edge-feed-gateway/src/main/java/app/feedgateway/RithmicDepthHistory.java:26` — `static final int MAX_COLUMNS`.
- `option-edge-feed-gateway/src/main/java/app/feedgateway/RithmicDepthHistory.java:43` — `private void run`.
- `option-edge-feed-gateway/src/main/java/app/feedgateway/RithmicDepthHistory.java:64` — `synchronized void accept`.
- `option-edge-feed-gateway/src/main/java/app/feedgateway/RithmicDepthHistory.java:78` — `String page`.
- `option-edge-feed-gateway/src/main/java/app/feedgateway/RithmicDepthHistory.java:102` — `static byte[] expand`.
- `option-edge-feed-gateway/src/main/java/app/feedgateway/FeedGatewayService.java:15277` — `void handleFootprintBasicMessage`.
- `option-edge-feed-gateway/src/main/java/app/feedgateway/FeedGatewayService.java:15327` — `private void send(WebSocketSession`.
- `options-edge/src/app/web/assets/context-tape-rithmic-liquidity.js:95` — `function historyCells`.
- `options-edge/src/app/web/assets/context-tape-rithmic-liquidity.js:152` — `function requestHistory`.
- `options-edge/src/app/web/assets/context-tape-rithmic-liquidity.js:162` — `function acceptHistory`.
- `options-edge/src/app/web/assets/context-tape-rithmic-liquidity.js:186` — `function syncZones`.
- `options-edge/src/app/web/assets/context-tape-rithmic-liquidity.js:240` — `ctx.drawImage`.
- `options-edge/src/app/web/assets/context-tape-rithmic-liquidity.js:19` — `function frame`.
- `options-edge/src/app/web/assets/context-tape-rithmic-liquidity.js:245` — `const end=latest`.
- `options-edge/src/app/web/assets/context-tape-order-tab.js:111` — `function edgeRecenter`.
- `options-edge/src/app/web/assets/context-tape-order-tab.js:1318` — `function followView`.
- `options-edge/src/app/web/assets/context-tape-order-tab.js:1674` — `data-liquidity-zone-fill`.
- `options-edge/src/app/web/assets/context-tape.js:3816` — `sendHistory:function`.

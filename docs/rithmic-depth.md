# Rithmic depth WebSocket relay

GATEWAY_RITHMIC_DEPTH_ENABLED defaults false. The optional relay tails options.rithmic.es-depth-spx from the existing runtime-profile Kafka/Schema Registry, decodes canonical contracts0.63.0 Avro, validates exact price translation and freshness, and forwards rithmic-liquidity through the existing authenticated WebSocket and bounded outbound channels. No market-data HTTP endpoint is added.

This fixed ESZ6/Rithmic Test overlay follows the gateway's existing shared ES-display stream model. It preserves its explicit Test provenance and uses a coalescing key for replaceable whole-book frames. A current bounded frame is replayed to a connecting socket in sequence with live forwarding; replay rechecks source, publication and basis clocks. Frames of another identity, malformed prices, invalid mapping or stale assertions are refused. Recent unavailable control frames can clear the visual without rejuvenating an old book.

The relay has one assigned display-topic tail per gateway replica, a single bounded latest frame, and no Kafka writes. A dedicated consumer isolates poison data or registry outages from unrelated display feeds. It does not recalculate basis, submit orders or bypass socket authentication. Kafka ingress is Avro; JSON exists only at the existing browser WebSocket boundary.

Build with the option-edge-feed-gateway image job, not the retired feed-gateway-deploy host-jar job. Its standard full tests run on the existing isolated es4 Jenkins worker. Local full testing passed1691tests and encountered the one Docker-unavailable integration error; the Jenkins Docker-backed suite remains a required build gate. Focused frame and Spring context tests pass. Kubernetes/Kafka/WebSocket/browser acceptance remains pending.

# Running the feed-gateway with multiple replicas

The gateway's market-data routing is **instance-local by design**: each replica runs its own Kafka
consumers and the `SessionRoutingEngine` / `AppSession` registry live in that replica's heap. A WebSocket
is served live/replay data only by the replica it is connected to. The Redis ticket store is shared, but a
shared ticket store alone does **not** make the session model multi-replica safe — a shared `AppSession`
registry would still not help, because the replica holding the socket is the only one whose Kafka consumer
routes that session's data.

Therefore the WebSocket upgrade for a session **must reach the same replica that minted its ticket**.

## What the gateway enforces (P1 — multi-replica ticket binding)

* Every ticket id is `­<instanceId>~<random>`, where `instanceId` is `GATEWAY_INSTANCE_ID` (else the
  hostname — the pod name in k8s — else `local`). It is reported at `/health` as `instanceId`.
* The WebSocket handshake checks the ticket prefix **before** redeeming it:
  * prefix == this replica → redeem and attach (the `AppSession` is here);
  * prefix != this replica → reject with `ticket bound to a different gateway instance (...)` **without**
    consuming the single-use ticket, so the correctly routed retry still works. The reject is counted
    (`HandshakeTicketAuthenticator.foreignInstanceRejections`) — a non-zero, growing count means sticky
    routing is broken.

This converts the previous silent failure ("socket attach failed / connection rejected" on the wrong
replica) into an explicit, observable rejection, and guarantees a ticket can only ever be redeemed on the
replica that owns the session.

## Required deployment configuration: sticky routing

Configure the WebSocket ingress / load balancer for **session affinity** so a given client's `/api/ws-ticket`
mint and its `/ws/events` upgrade land on the same replica:

* **Kubernetes Service**: `sessionAffinity: ClientIP` (and align ingress affinity), or
* **nginx ingress**: `nginx.ingress.kubernetes.io/affinity: cookie` with a stable cookie, or
* any L7 LB cookie/consistent-hash affinity keyed on the client.

Give each replica a stable, unique `GATEWAY_INSTANCE_ID` (the k8s pod name via the downward API works:
`valueFrom.fieldRef.fieldPath: metadata.name`).

## Caveat: per-instance limits are not a global admission cap

`ConcurrencyLimits` (`maxTotalAppSessions`, per-user/socket caps) are enforced **per replica**. With sticky
routing the global total is roughly `replicas × maxTotalAppSessions`. To bound total load:

* size `GATEWAY_MAX_TOTAL_APP_SESSIONS` ≈ `globalCap / replicaCount`, **or**
* enforce a hard global cap with a shared (Redis) admission counter — a future enhancement; the per-replica
  limits are a local safety valve, not a cluster-wide quota.

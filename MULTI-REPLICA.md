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

## Multi-cluster (future, NOT yet implemented)

The instance binding above is cluster-agnostic, but extending past one cluster needs three things — none
are in place today, so multi-cluster is **not supported as-is**:

1. **Globally-unique instance ids.** The default id is the pod name, which collides across clusters
   (`feed-gateway-0` exists in every cluster). A foreign-cluster ticket whose prefix happens to match a
   same-named local pod would redeem and then fail with no local AppSession — the original bug. Qualify the
   id with the cluster: `GATEWAY_INSTANCE_ID=<cluster>/<pod>` (the `/` is preserved; only `~` is stripped).
2. **Cluster-sticky global routing.** The global tier (geo-DNS / global LB) must send a client to the *same
   cluster* for both the `/api/ws-ticket` mint and the `/ws/events` upgrade; in-cluster affinity then pins
   the pod. The instance binding remains the safety net that fails loudly on any leak.
3. **A deliberate data-plane model:**
   * **Cluster-as-shard (recommended):** pin each user to one cluster (consistent hash / geo). Ticket store
     (Redis), Kafka, and the admission counter are all per-cluster; the global cap is the sum of per-cluster
     sub-quotas (`cap_per_cluster ≈ globalCap / clusters`). Each cluster is self-contained — no cross-region
     Redis on the hot path.
   * **Single global plane:** one shared/replicated Redis + Kafka + one shared admission counter gives a true
     single global cap, at the cost of cross-region latency on the mint/admission path and a shared failure
     domain.

   The shared Redis admission counter (above) yields a true *global* cap only in the single-global-plane
   variant; under cluster-as-shard you use per-cluster sub-quotas instead.

Regardless of model, identity/approval must be globally consistent across clusters: the Keycloak issuer (or
federation), the approval platform (`GATEWAY_APPROVAL_URL`), and the replay orchestrator must be reachable
and authoritative from every cluster.

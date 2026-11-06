# Native full-book history — in progress

This branch implements the gateway side of the approved RLF1 full-book wire contract, OptionsEdge PR1565. It is coordinated with six-phase reconciliation https://github.com/abhinav-jain09/options-edge/pull/1546 under OP-P4-02. No protocol capability is advertised or activated yet.

The strict request parser rejects price-slice fields, request-supplied subject, unknown fields, foreign system/exchange/contract, unsafe/coerced timestamps, future/end/cursor violations and invalid request identifiers. It requires an explicitly configured Rithmic Test or Rithmic Paper Trading namespace; there is no implicit promotion. Missing configuration refuses the opt-in route without changing the existing JSON route. The requestId retained in errors is bounded.

Local preliminary validation: mvn -q -Dtest=RithmicNativeHistoryRequestTest test,5 request tests pass with0 failures/errors/skips; the existing isolated context smoke also ran. This is not the required Linux/Jenkins release validation.

Remaining: typed ingest provenance and shared128MiB cache; configured-scope filtering before sampling; source-gap/retention/incarnation/late-revision metadata; bounded RLF1 encoder; tracked authenticated binary queue with UTF-8 accounting and reserved control capacity; authorization at enqueue and write; capability/availability/invalidation controls; Linux full suite, reviews, Jenkins dev-only rollout and real authenticated Edge acceptance. Production is out of scope.

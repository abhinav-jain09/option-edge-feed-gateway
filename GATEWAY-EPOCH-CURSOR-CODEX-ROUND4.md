No findings.

Verified:

- Both Surefire formats normalize to `Class.method` at `scripts/footprint-mutate.py:27`.
- All 19 record entries have valid anchors, occurrence counts, source lines, enclosing declarations, and status/`killedBy` consistency.
- Every `killedBy` references an actual test method; none names a package.
- The three documented harness defects and epoch-edge behavior are supported by the code and regenerated records.
- `git diff --check` passes.
- Maven execution was blocked only by the read-only workspace preventing writes to `target/`.

VERDICT: APPROVE

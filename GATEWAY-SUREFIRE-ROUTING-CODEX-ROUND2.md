No findings.

The HIGH is withdrawn. The measurement is valid: under Jupiter’s default `PER_METHOD` lifecycle, static user `@BeforeAll` runs before test-instance creation. Spring Test 6.2.7 loads the context through `SpringExtension.postProcessTestInstance()` → `TestContextManager.prepareTestInstance()`, which occurs afterward. An assertion failure therefore prevents context refresh. The passing Surefire report cannot establish the opposite ordering.

The three MEDIUM fixes are correct:

- [pom.xml:160](/private/tmp/oe-conf-gw/pom.xml:160) accurately limits the routing guarantee to `-Dtest=` and disclaims `-Dgroups`, `-DexcludedGroups`, and direct `surefire:test`.
- [GatewayContextSmokeTest.java:82](/private/tmp/oe-conf-gw/src/test/java/app/feedgateway/GatewayContextSmokeTest.java:82) correctly states that the tag excludes the smoke test from `default-test`, while the pinned execution’s `<test>` selects it.
- [pom.xml:162](/private/tmp/oe-conf-gw/pom.xml:162) correctly documents that `mvn surefire:test` uses `default-cli`; the fail-closed `@BeforeAll` guard remains effective if the class is reached without the environment pins.

VERDICT: APPROVE

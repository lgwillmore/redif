# Roadmap

Status: active backlog, written on 2026-11-14 when the library moved to Maven Central. Current implementation rules
live in [`AGENTS.md`](../AGENTS.md).

## Remaining work

1. **Atomic counting with expiry.** An `incr` (or `incrby`) in the strings group, alongside `pexpire`, so a caller
   can keep a rate limit or attempt count that holds under parallel callers: today a count has to be read and
   `set`, which races. Redis `INCR` on a missing key starts at zero; the in-memory implementation does the same under
   its mutex.
2. **Hashes and sets** as they are needed, each as its own command group that `RedifAPI` extends.
3. **A published test kit.** `RedifContract` could be published as `redif-core`'s test-fixtures variant so a consumer
   implementing `RedifAPI` over another client can prove it against the same contract.

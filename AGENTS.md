# Redif

Redif is a Redis facade for Kotlin/JVM: one suspending interface, `RedifAPI`, over the Redis commands an application
needs, with an in-memory implementation and a Lettuce client held to the same contract. Keep it a facade: it names
Redis commands and their Redis semantics, it does not invent abstractions over them, and an application that needs a
command Redif lacks adds the command rather than working around it.

## Repository layout

| Module | Artifact | Packages | Contents |
| --- | --- | --- | --- |
| `core` | `redif-core` | `codes.laurence.redif`, `codes.laurence.redif.commands.*` | `RedifAPI`, the command groups it extends with their models, `RedifCommandException` and `RedifClientConfig`. No implementation. Depends on kotlinx-coroutines only. |
| `in-memory` | `redif-in-memory` | `codes.laurence.redif.inmemory` | `RedifInMemory`, the facade kept in the process. |
| `lettuce` | `redif-lettuce` | `codes.laurence.redif.lettuce` | `RedifLettuce`, the facade over a Redis instance through `io.lettuce:lettuce-core`'s coroutine commands. |
| `compatibility` | `redif-compatibility` | `codes.laurence.redif.compatibility` | The contract: one suite of cases for each command group, which any implementation's tests extend. |

- Published under the Maven group `codes.laurence.redif`, which is also the root Kotlin package. Keep the two in step.
- Core never depends on an implementation. An implementation depends on core through `api`, since it implements
  core's interfaces, and on `redif-compatibility` in its tests only. Public API changes are breaking for every
  consumer; prefer additions.
- Each implementation is one public class that delegates each command group to an internal class in its own file
  (`InMemoryHashes`, `LettuceHashes`), so a new group is a new file on each side.

## Commands

- A command is a `suspend` member of a command group interface under `commands/<group>/api.kt`, named after the Redis
  command in lower case (`psetx`, `setnx`, `zremrangebyscore`), with Redis's own argument order and return meaning
  (`setnx` answers whether it set, `pexpire` whether the key existed, `del` how many keys went). Times are
  milliseconds, as in the `p`-prefixed Redis commands. `scan` is the one member that is not `suspend`: it returns a
  `Flow` that runs the cursor.
- The groups are `StringsAPI`, `KeysAPI`, `HashesAPI`, `SetsAPI`, `SortedSetsAPI` and `HyperLogLogAPI`. `RedifAPI`
  extends every one, and `AutoCloseable`, so a consumer holds one type. A new group is a new interface that
  `RedifAPI` extends, with its models in `models.kt` beside it.
- A command's options are a data class named as Redis names them (`SetArgs`, `ZAddArgs`, `ExpireCondition`). A
  combination Redis refuses outright (`PX` with `KEEPTTL`, `NX` with `GT`) fails in the class's `init`, so both
  implementations refuse it alike.
- **No invented composites.** Where two commands seem to need to be one, find how Redis itself solves it:
  - A count with an expiry is `incrby` then `pexpire` with `ExpireCondition.NX`. `NX` sets an expiry only when the key
    has none, so a caller that died between the two leaves a key the next caller gives an expiry to.
  - Live holds are a sorted set scored by last heartbeat: `zremrangebyscore` for the stale, then `zcard`. Trimming
    twice changes nothing, so the two need not be atomic.
- **Errors.** Where Redis refuses a command, the member throws `RedifCommandException` with the first word of
  Redis's error as its `code` (`WRONGTYPE`, `ERR`). `RedifLettuce` maps Lettuce's `RedisCommandExecutionException` in
  `redis { }`, which every Lettuce call goes through; `RedifInMemory` throws the same code wherever Redis would. The
  contract asserts the code, never the message.
- **Degenerate arguments.** Where Redis would call an empty map or an empty vararg a syntax error, both
  implementations do nothing and answer zero or empty. Where Redis accepts it (`pfadd` with no elements creates the
  key), both do as Redis does.
- `del` takes glob patterns, as `KEYS` does, and both implementations expand them: `*`, `?`, `[abc]`, `[a-z]`, `[^a]`
  and `\` to escape. `RedifInMemory` translates the glob to a regex; `RedifLettuce` deletes a key with no glob
  character directly and expands a pattern with `SCAN` before deleting the matches, which is not atomic and walks
  the keyspace, so callers use patterns for cleanup, not on hot paths.
- **No batch API.** Lettuce writes a command without waiting for the reply to the one before, so commands from
  coroutines running together on the shared connection are already pipelined. `RedifLettuceClientTest` proves it;
  add an explicit batch only if that test stops holding.
- **Redis 7.0 is the floor** (`PEXPIRE … NX`, `SET … NX GET`). A command that needs a later Redis waits until the
  floor moves; CI runs the Lettuce tests against 7.0 as well as the newest so one cannot slip in.

## RedifInMemory

- `Keyspace` maps each key to one typed `Value` (string, hash, set, sorted set, HyperLogLog) with the key's expiry
  beside it, so `pexpire`, `del`, `exists`, `type` and the lazy expiry on read work for every type with no code per
  type. A command against a key of another type is `WRONGTYPE`, through `Keyspace.typed`.
- One `Mutex` guards the keyspace, and each command is one `keyspace.locked { }`, so each is atomic as it is in
  Redis. Keys expire lazily on read by the `Clock`, which tests can replace.
- A hash, set or sorted set that empties removes its key, as Redis does: call `removeIfEmpty` after removing.
- A HyperLogLog is an exact set, which is within any error Redis allows, and its `type` is `string`, as Redis's is.

## Testing

- **The contract comes first.** Every command has its cases in its group's suite in `redif-compatibility`
  (`StringsContract`, `SortedSetsContract`, …), once, before either implementation. Each implementation's tests
  supply a `RedifHarness` and extend every suite (`RedifInMemoryTest.kt`, `RedifLettuceTest.kt`), so the two can
  never drift apart. A new suite needs a line in both files.
- The suites are published for anyone implementing `RedifAPI` over another client, so they are API: a case makes
  its own keys with `randString()`, never assumes an empty Redis and never clears one, and asserts only what Redis
  promises (a HyperLogLog count within its error, `scan` results as a set).
- What is particular to one implementation is tested in its own module, beside its contract classes.
- `./gradlew test` runs everything. `redif-lettuce`'s tests need the Redis that `docker compose up -d --wait`
  starts on 6377 (`REDIF_TEST_REDIS_HOST` and `REDIF_TEST_REDIS_PORT` override it, and `REDIF_TEST_REDIS_VERSION`
  picks the image); the other modules need nothing.
- Tests use `kotlin.test` and `assertk`, with full function bodies and nested `runBlocking` for suspend calls.
- Before submitting a PR, run `./gradlew ktfmtFormat`; CI runs `ktfmtCheck`.

Outstanding work is tracked in [`docs/roadmap.md`](docs/roadmap.md).

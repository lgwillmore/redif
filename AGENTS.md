# Redif

Redif is a Redis facade for Kotlin/JVM: one suspending interface, `RedifAPI`, over the Redis commands an application
needs, with an in-memory implementation and a Lettuce client held to the same contract. Keep it a facade: it names
Redis commands and their Redis semantics, it does not invent abstractions over them, and an application that needs a
command Redif lacks adds the command rather than working around it.

## Repository layout

| Module | Artifact | Packages | Contents |
| --- | --- | --- | --- |
| `core` | `redif-core` | `codes.laurence.redif`, `codes.laurence.redif.commands.*` | `RedifAPI` and the command groups it extends (`KeysAPI`, `StringsAPI`), `RedifClientConfig`, and `RedifInMemory`. Depends on kotlinx-coroutines only. Its test fixtures hold `RedifContract`, not published. |
| `lettuce` | `redif-lettuce` | `codes.laurence.redif.lettuce` | `RedifLettuce`, the facade over a Redis instance through `io.lettuce:lettuce-core`'s coroutine commands. Depends on core through `api`. |

- Published under the Maven group `codes.laurence.redif`, which is also the root Kotlin package. Keep the two in step.
- Core never depends on a client. A client depends on core through `api`, since it implements core contracts and is
  configured with core's `RedifClientConfig`. Public API changes are breaking for every consumer; prefer additions.

## Commands

- A command is a `suspend` member of a command group interface under `commands`, named after the Redis command in
  lower case (`psetx`, `setnx`, `pexpire`), with Redis's own argument order and return meaning (`setnx` answers
  whether it set, `pexpire` whether the key existed, `del` how many keys went). Times are milliseconds, as in the
  `p`-prefixed Redis commands.
- `RedifAPI` extends every command group, so a consumer holds one type. A new group is a new interface that
  `RedifAPI` extends.
- `del` takes glob patterns, as `KEYS` does, and both implementations expand them: `*`, `?`, `[abc]`, `[a-z]` and
  `[^a]`. The in-memory implementation translates the glob to a regex in `RedifInMemory`; Lettuce asks Redis with
  `KEYS` and then `DEL`s the matches, which is not atomic and scans the keyspace, so callers use it for cleanup, not
  on hot paths.
- `RedifInMemory` guards its map with one `Mutex`, so each command is atomic as it is in Redis, and expires keys
  lazily on read with its `Clock`, which tests can replace.
- Every command is covered by `RedifContract`, once, and every implementation's test extends the contract, so the
  in-memory implementation and Lettuce can never drift apart. A new command is a new contract case first.

## Testing

- `./gradlew test` runs everything. `redif-lettuce`'s tests need the Redis that `docker compose up -d --wait`
  starts on 6377 (`REDIF_TEST_REDIS_HOST` and `REDIF_TEST_REDIS_PORT` override it); `redif-core`'s need nothing.
- Tests use `kotlin.test` and `assertk`, with full function bodies and nested `runBlocking` for suspend calls.
- Before submitting a PR, run `./gradlew ktfmtFormat`; CI runs `ktfmtCheck`.

Outstanding work is tracked in [`docs/roadmap.md`](docs/roadmap.md).

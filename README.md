# Redif

[![Check](https://github.com/lgwillmore/redif/actions/workflows/check.yml/badge.svg?branch=main)](https://github.com/lgwillmore/redif/actions/workflows/check.yml)
[![Maven Central](https://img.shields.io/maven-central/v/codes.laurence.redif/redif-core)](https://central.sonatype.com/namespace/codes.laurence.redif)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Redif is a small Redis facade for Kotlin on the JVM. Your code talks to `RedifAPI`, a suspending interface over the
Redis commands an application reaches for, and you choose the implementation at wiring time:

- `RedifInMemory` keeps the keys in the process, with expiry. No Redis to run: for tests, single-instance caching
  and development.
- `RedifLettuce` is the same interface over a real Redis through [Lettuce](https://lettuce.io/).

The two are held to one contract, so a service tested against the in-memory implementation behaves the same way
against Redis.

## Install

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    implementation("codes.laurence.redif:redif-core:<version>")
    implementation("codes.laurence.redif:redif-lettuce:<version>")       // the Lettuce client
    testImplementation("codes.laurence.redif:redif-in-memory:<version>") // or `implementation`, to run without Redis
}
```

| Artifact | Contents |
| --- | --- |
| `redif-core` | `RedifAPI`, its command groups and `RedifClientConfig` |
| `redif-in-memory` | `RedifInMemory`, a `RedifAPI` kept in the process |
| `redif-lettuce` | `RedifLettuce`, a `RedifAPI` over a Redis instance through `io.lettuce:lettuce-core` |
| `redif-compatibility` | The contract suites, for testing your own `RedifAPI` implementation |

Requires JDK 17 or later, and for `RedifLettuce` Redis 7.0 or later.

## Use

```kotlin
val redis: RedifAPI =
    if (config.redis == null) RedifInMemory()
    else RedifLettuce(RedifClientConfig(host = config.redis.host, port = config.redis.port))

redis.psetx("session:$id", token, expireMillis = 900_000)
val token = redis.get("session:$id")

redis.close() // on shutdown
```

A member of `RedifAPI` is the Redis command of the same name, with Redis's argument order and return meaning, so
the [Redis command reference](https://redis.io/docs/latest/commands/) is the reference for each.

## Commands

| Group | Commands |
| --- | --- |
| Strings | `set` (with `NX`, `XX`, `PX`, `KEEPTTL`, `GET`), `psetx`, `setnx`, `get`, `mget`, `mset`, `getdel`, `incrby`, `decrby` |
| Keys | `pexpire` (with `NX`, `XX`, `GT`, `LT`), `del` (with Redis glob patterns), `exists`, `pttl`, `persist`, `type`, `scan` |
| Hashes | `hset`, `hget`, `hmget`, `hgetall`, `hdel`, `hexists`, `hlen`, `hincrby` |
| Sets | `sadd`, `srem`, `scard`, `sismember`, `smembers` |
| Sorted sets | `zadd` (with `NX`, `XX`, `GT`, `LT`, `CH`), `zrem`, `zcard`, `zscore`, `zincrby`, `zcount`, `zrange` (by rank or score, with `REV` and `LIMIT`), `zremrangebyscore` |
| HyperLogLog | `pfadd`, `pfcount`, `pfmerge` |

**Errors.** Where Redis refuses a command, such as one against a key holding another type, the call throws
`RedifCommandException`. Branch on its `code`, the first word of Redis's error (`WRONGTYPE`, `ERR`), not on its
message.

**A count with an expiry**, for a rate limit, is two commands and needs no transaction:

```kotlin
val count = redis.incrby("limit:$caller", 1)
redis.pexpire("limit:$caller", 60_000, ExpireCondition.NX) // only the window's first caller sets it
```

**Pipelining** needs no batch API. `RedifLettuce` sends every command down one connection without waiting for the
reply to the one before, so commands from coroutines running at the same time share round trips.

## Testing your own implementation

`redif-compatibility` holds the contract as one JUnit 5 suite for each command group. Write a harness that hands the
suites your client, and a class for each suite:

```kotlin
dependencies {
    testImplementation("codes.laurence.redif:redif-compatibility:<version>")
}
```

```kotlin
object MyHarness : RedifHarness {
    private val shared = MyRedif(config)

    override fun client(): RedifAPI = shared

    // Optional hooks, each given the client of the case:
    override suspend fun beforeEach(client: RedifAPI) {}
    override suspend fun afterEach(client: RedifAPI) {}
}

class MyHashesTest : HashesContract(MyHarness)
class MyHyperLogLogTest : HyperLogLogContract(MyHarness)
class MyKeysTest : KeysContract(MyHarness)
class MySetsTest : SetsContract(MyHarness)
class MySortedSetsTest : SortedSetsContract(MyHarness)
class MyStringsTest : StringsContract(MyHarness)
```

Every case makes its own random keys, so the cases can share one client and one Redis, and nothing needs clearing
between them.

## Contributing

Requires JDK 17 and Docker for the Lettuce tests.

```shell
docker compose up -d --wait   # Redis on 6377 for redif-lettuce
./gradlew test
./gradlew ktfmtFormat         # before opening a PR
```

- [`AGENTS.md`](AGENTS.md) holds the design rules the library is built to.
- [`docs/roadmap.md`](docs/roadmap.md) lists outstanding work.
- Releases are described in [`PUBLISHING_SETUP.md`](PUBLISHING_SETUP.md).

Licensed under [MIT](LICENSE).

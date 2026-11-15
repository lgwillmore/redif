# Redif

[![Check](https://github.com/lgwillmore/redif/actions/workflows/check.yml/badge.svg?branch=main)](https://github.com/lgwillmore/redif/actions/workflows/check.yml)
[![Maven Central](https://img.shields.io/maven-central/v/codes.laurence.redif/redif-core)](https://central.sonatype.com/namespace/codes.laurence.redif)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

Redif is a small Redis facade for Kotlin on the JVM. Your code talks to `RedifAPI`, a suspending interface over the
handful of Redis commands it needs, and you choose the implementation at wiring time:

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
    implementation("codes.laurence.redif:redif-lettuce:<version>") // the Lettuce client
}
```

| Artifact | Contents |
| --- | --- |
| `redif-core` | `RedifAPI`, `RedifClientConfig` and `RedifInMemory` |
| `redif-lettuce` | `RedifLettuce`, a `RedifAPI` over a Redis instance through `io.lettuce:lettuce-core` |

Requires JDK 17 or later.

## Use

```kotlin
val redis: RedifAPI =
    if (config.redis == null) RedifInMemory()
    else RedifLettuce(RedifClientConfig(host = config.redis.host, port = config.redis.port))

redis.psetx("session:$id", token, expireMillis = 900_000)
val token = redis.get("session:$id")
```

## Commands

Commands are added as they are needed. Today:

| Group | Commands |
| --- | --- |
| Keys | `pexpire`, `del` (with Redis glob patterns) |
| Strings | `set`, `psetx`, `setnx`, `get` |

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

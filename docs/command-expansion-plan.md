# Command expansion plan

Status: a brief for the work, written on 2026-11-26. Nothing in it is built. The rules it must keep are in
[`AGENTS.md`](../AGENTS.md); read that first.

## Why

Redif has six commands: `set`, `psetx`, `setnx`, `get`, `pexpire` and `del`. Plurex, its first consumer, is about
to use Redis for much more:

- **Shared counts.** Rate limits, and caps on how much an org may hold at once.
- **Live holds.** Which markets an org holds, where a hold must disappear when its holder dies without releasing
  it.
- **Published state.** Whether an org has credit left, read by a guard on every request.

The aim is one release that covers these and the wider set of Redis an application reaches for next, so the
interface does not grow one command at a time.

Streams and pub/sub were in the first draft of this plan and were dropped on 2026-11-26: they reach beyond what the
facade exists for.

## What stays true

- **Redif is a facade.** A member is a Redis command, in lower case, with Redis's argument order and return
  meaning. This plan adds no invented composite. Where two commands would have to be one, it says how Redis itself
  solves it.
- **One interface per Redis command group**, under `commands/<group>/api.kt`, with the group's models beside it.
  `RedifAPI` extends every group.
- **The contract comes first.** Every command gets its cases in `RedifContract` before either implementation, and
  both implementations pass the same cases.
- **Additions only.** Nothing public today changes its signature or its meaning.

## Order of work

1. Groundwork: the three changes every later step leans on.
2. The command groups that are plain request and response.
3. The two decisions about the client's shape.

Each step is its own pull request, with `AGENTS.md` and the README updated in it.

## 1. Groundwork

**A keyspace of typed values in `RedifInMemory`.** Today it is a map of strings. It becomes a map of keys to a
sealed value (string, hash, set, sorted set, HyperLogLog), each with the key's expiry, so that:

- One key holds one type, as in Redis.
- `pexpire`, `del`, `exists` and the lazy expiry on read work for every type with no code per type.
- An empty hash, set or sorted set removes its key, as Redis does.

**Errors.** Redis refuses some commands with an error whose first word is a code, such as `WRONGTYPE`. Nothing in redif models that today.

- Add `RedifCommandException(code, message)` in core.
- `RedifLettuce` turns Lettuce's `RedisCommandExecutionException` into it.
- `RedifInMemory` throws it wherever Redis would, with the same code.
- The contract asserts the code, never the message.

**Closing.** `RedifLettuce` opens a client and never shuts it down. `RedifAPI` becomes `AutoCloseable`.

## 2. Command groups

All of these are a request and a reply on the shared connection. In `RedifInMemory` each is a few lines under the
mutex.

### Strings (`StringsAPI`, extended)

| Command | Signature | Notes |
|---|---|---|
| `SET` with options | `set(key, value, args: SetArgs): String?` | `NX`, `XX`, `PX`, `KEEPTTL`, `GET`. Returns the old value with `GET`, otherwise `"OK"` or null when a condition stopped it. `setnx` and `psetx` stay |
| `MGET` | `mget(vararg keys): List<String?>` | In key order, null for a missing key or one of another type |
| `MSET` | `mset(values: Map<String, String>)` | |
| `GETDEL` | `getdel(key): String?` | |
| `INCRBY` | `incrby(key, by: Long): Long` | A missing key starts at zero. `WRONGTYPE`-style error when the value is not an integer |
| `DECRBY` | `decrby(key, by: Long): Long` | |

**A count with an expiry needs no composite.** `incrby` followed by `pexpire` with the `NX` condition (below) is
two calls, and it heals itself: `NX` sets an expiry only when the key has none, so a caller that died between the
two leaves a key the next caller gives an expiry to. That is a fixed window, and it is how the roadmap's first
item is closed.

### Keys (`KeysAPI`, extended)

| Command | Signature | Notes |
|---|---|---|
| `PEXPIRE` with a condition | `pexpire(key, expireMillis, condition: ExpireCondition): Boolean` | `NX`, `XX`, `GT`, `LT`. Needs Redis 7.0. The existing two-argument `pexpire` stays |
| `EXISTS` | `exists(vararg keys): Long` | Counts a key once for each time it is named, as Redis does |
| `PTTL` | `pttl(key): Long` | `-2` for no key, `-1` for no expiry, as Redis answers |
| `PERSIST` | `persist(key): Boolean` | |
| `TYPE` | `type(key): String` | `none` for no key |
| `SCAN` | `scan(match: String? = null, count: Long? = null): Flow<String>` | The cursor loop inside the flow. May repeat a key, as `SCAN` may |

`del` keeps its glob behaviour. Move `RedifLettuce.del` from `KEYS` to `scan` while here, so it no longer stalls
Redis on a large keyspace; it stays non-atomic.

### Hashes (`HashesAPI`, new)

`hset(key, fields: Map<String, String>): Long`, `hget`, `hmget`, `hgetall`, `hdel`, `hexists`, `hlen`,
`hincrby`.

### Sets (`SetsAPI`, new)

`sadd`, `srem`, `scard`, `sismember`, `smembers`.

### Sorted sets (`SortedSetsAPI`, new)

| Command | Signature |
|---|---|
| `ZADD` | `zadd(key, members: Map<String, Double>, args: ZAddArgs? = null): Long` with `NX`, `XX`, `GT`, `LT`, `CH` |
| `ZREM` | `zrem(key, vararg members): Long` |
| `ZCARD` | `zcard(key): Long` |
| `ZSCORE` | `zscore(key, member): Double?` |
| `ZINCRBY` | `zincrby(key, by: Double, member): Double` |
| `ZCOUNT` | `zcount(key, min: ScoreBound, max: ScoreBound): Long` |
| `ZRANGE` by rank and by score | `zrange(key, range, withScores)`, returning members with their scores |
| `ZREMRANGEBYSCORE` | `zremrangebyscore(key, min: ScoreBound, max: ScoreBound): Long` |

`ScoreBound` is inclusive, exclusive or infinite, as Redis's `(1.5`, `-inf` and `+inf` are. Members with equal
scores order by member, byte-wise.

This is the group live holds use: the score is the hold's last heartbeat, and a count is `zremrangebyscore` for
what is older than a minute and then `zcard`. The two are not atomic and need not be, since trimming twice changes
nothing.

### HyperLogLog (`HyperLogLogAPI`, new)

`pfadd`, `pfcount`, `pfmerge`. `RedifInMemory` keeps an exact set, which is within any error Redis allows. The
contract asserts a count within Redis's stated error (about 1%), never an exact one, on a few thousand members.

## 3. Decisions about the client's shape

Each of these is about how the interface reads, so each is decided and written into `AGENTS.md` before its code. The recommendation is given; raise it with the owner if the code shows
it to be wrong.

### 3.1 Pipelining

**Recommended: add nothing, and prove it.** Lettuce writes a command without waiting for the reply to the one
before, so coroutines that call the shared connection at the same time are already pipelined. Write one Lettuce
test that sends a few thousand `set`s from concurrent coroutines and shows they complete in far fewer round trips'
worth of time than the same sent one after another, and say so in the README. Add an explicit batch API only if
that test shows the claim to be false.

### 3.2 Commands that must be atomic together

The facade rule rules out invented composites, and this plan needs none for counting (section 2) or for live holds
(sorted sets). The one case left is releasing a lock only if you still hold it: compare, then delete.

**Recommended: leave it out of this release.**

- Check first whether the Redis version in the floor below has a native conditional delete. If it does, that is a
  command like any other and goes in.
- If it does not, the honest facade for it is `EVAL`, which `RedifInMemory` cannot run. Do not add a scripting
  group that only one implementation can honour. Record it in the roadmap as open.

## Redis version floor

| Needs | Since |
|---|---|
| `GETDEL` | 6.2 |
| `PEXPIRE … NX/XX/GT/LT` | 7.0 |

**The floor is Redis 7.0.** State it in the README.

- This repo's tests run on `redis:8.2.1`, so they would not catch a command that 7.0 lacks. Add a second CI run of
  the Lettuce tests against `redis:7.0`.
- Plurex's local plane runs `redis:6.2.3` and must move to 7 or later before it uses the conditional expiry. That
  is Plurex's change, noted here so it is not a surprise.
- Check that the Lettuce version in `gradle/libs.versions.toml` has every command above in its coroutines API,
  and move it up if not.

## Done when

- Every command above has contract cases, and `RedifInMemoryTest` and `RedifLettuceTest` both pass them.
- `AGENTS.md` lists the new groups and the two decisions as they were made; the README shows the groups and the
  version floor.
- `docs/roadmap.md` no longer lists counting, hashes or sets, and keeps the published test kit and anything left
  open in 3.2.
- `./gradlew ktfmtFormat` has been run.

Publishing the release is the owner's step and is not part of this work.

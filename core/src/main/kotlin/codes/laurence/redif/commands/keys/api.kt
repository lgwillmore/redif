package codes.laurence.redif.commands.keys

import kotlinx.coroutines.flow.Flow

interface KeysAPI {
  /** Answers whether the key existed. A time of zero or less deletes the key. */
  suspend fun pexpire(key: String, expireMillis: Long): Boolean

  /** Answers whether the expiry was set: false for no key, or when [condition] stopped it. */
  suspend fun pexpire(key: String, expireMillis: Long, condition: ExpireCondition): Boolean

  /** Each key is a glob pattern, as in `KEYS`. Answers how many keys went. */
  suspend fun del(vararg keys: String): Long

  /** How many of the keys exist, counting a key once for each time it is named. */
  suspend fun exists(vararg keys: String): Long

  /** The milliseconds the key has left: `-2` for no key and `-1` for a key with no expiry. */
  suspend fun pttl(key: String): Long

  /** Removes the key's expiry. Answers whether it had one. */
  suspend fun persist(key: String): Boolean

  /** `string`, `hash`, `set` or `zset`, and `none` for no key. */
  suspend fun type(key: String): String

  /**
   * Every key matching the glob [match], or every key. The flow runs `SCAN`'s cursor to its end as
   * it is collected, [count] being the hint given to each call, and, as `SCAN` may, it can name a
   * key twice or miss one written while it runs.
   */
  fun scan(match: String? = null, count: Long? = null): Flow<String>
}

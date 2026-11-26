package codes.laurence.redif.inmemory

import codes.laurence.redif.commands.keys.ExpireCondition
import codes.laurence.redif.commands.keys.KeysAPI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

internal class InMemoryKeys(private val keyspace: Keyspace) : KeysAPI {

  override suspend fun pexpire(key: String, expireMillis: Long): Boolean =
      keyspace.locked { expire(key, expireMillis, null) }

  override suspend fun pexpire(
      key: String,
      expireMillis: Long,
      condition: ExpireCondition,
  ): Boolean = keyspace.locked { expire(key, expireMillis, condition) }

  private fun Keyspace.expire(key: String, millis: Long, condition: ExpireCondition?): Boolean {
    val entry = find(key) ?: return false
    val current = entry.expireAt
    val next = after(millis)
    // A key with no expiry never expires: later than any time, so GT never applies and LT always.
    val allowed =
        when (condition) {
          null -> true
          ExpireCondition.NX -> current == null
          ExpireCondition.XX -> current != null
          ExpireCondition.GT -> current != null && next > current
          ExpireCondition.LT -> current == null || next < current
        }
    if (!allowed) return false
    if (millis <= 0) remove(key) else entry.expireAt = next
    return true
  }

  override suspend fun del(vararg keys: String): Long =
      keyspace.locked {
        val live = keys()
        var count = 0L
        keys.forEach { pattern ->
          val regex = globToRegex(pattern)
          live.forEach { key -> if (regex.matches(key) && remove(key)) count++ }
        }
        count
      }

  override suspend fun exists(vararg keys: String): Long =
      keyspace.locked { keys.count { find(it) != null }.toLong() }

  override suspend fun pttl(key: String): Long =
      keyspace.locked {
        val entry = find(key) ?: return@locked -2L
        val expireAt = entry.expireAt ?: return@locked -1L
        (expireAt - now()).inWholeMilliseconds
      }

  override suspend fun persist(key: String): Boolean =
      keyspace.locked {
        val entry = find(key)
        if (entry?.expireAt == null) {
          false
        } else {
          entry.expireAt = null
          true
        }
      }

  override suspend fun type(key: String): String =
      keyspace.locked { find(key)?.value?.type ?: "none" }

  /** One pass over the keys there are when collection starts, so it never repeats a key. */
  override fun scan(match: String?, count: Long?): Flow<String> = flow {
    val regex = match?.let { globToRegex(it) }
    val matching = keyspace.locked { keys().filter { regex == null || regex.matches(it) } }
    matching.forEach { emit(it) }
  }
}

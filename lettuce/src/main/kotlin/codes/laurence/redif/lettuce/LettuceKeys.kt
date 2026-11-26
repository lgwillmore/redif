package codes.laurence.redif.lettuce

import codes.laurence.redif.commands.keys.ExpireCondition
import codes.laurence.redif.commands.keys.KeysAPI
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ExpireArgs
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toSet

@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class LettuceKeys(connection: LettuceConnection) : KeysAPI {

  private val commands = connection.commands

  override suspend fun pexpire(key: String, expireMillis: Long): Boolean = redis {
    commands.pexpire(key, expireMillis).check()
  }

  override suspend fun pexpire(
      key: String,
      expireMillis: Long,
      condition: ExpireCondition,
  ): Boolean {
    val args =
        when (condition) {
          ExpireCondition.NX -> ExpireArgs.Builder.nx()
          ExpireCondition.XX -> ExpireArgs.Builder.xx()
          ExpireCondition.GT -> ExpireArgs.Builder.gt()
          ExpireCondition.LT -> ExpireArgs.Builder.lt()
        }
    return redis { commands.pexpire(key, expireMillis, args).check() }
  }

  /**
   * A key with nothing of a glob in it is deleted as it is. A pattern is expanded with `SCAN`, so
   * it walks the keyspace without stalling Redis, and the matches are then deleted: not atomic.
   */
  override suspend fun del(vararg keys: String): Long {
    var count = 0L
    keys.forEach { pattern ->
      val matching =
          if (pattern.none { it in GLOB_CHARACTERS }) listOf(pattern)
          else scan(match = pattern, count = SCAN_COUNT).toSet()
      matching.chunked(DEL_CHUNK).forEach { chunk ->
        count += redis { commands.del(*chunk.toTypedArray()).check() }
      }
    }
    return count
  }

  override suspend fun exists(vararg keys: String): Long {
    if (keys.isEmpty()) return 0
    return redis { commands.exists(*keys).check() }
  }

  override suspend fun pttl(key: String): Long = redis { commands.pttl(key).check() }

  override suspend fun persist(key: String): Boolean = redis { commands.persist(key).check() }

  override suspend fun type(key: String): String = redis { commands.type(key).check() }

  override fun scan(match: String?, count: Long?): Flow<String> = flow {
    val args = ScanArgs()
    match?.let { args.match(it) }
    count?.let { args.limit(it) }
    var cursor: ScanCursor = ScanCursor.INITIAL
    do {
      val page = redis { commands.scan(cursor, args).check() }
      page.keys.forEach { emit(it) }
      cursor = page
    } while (!page.isFinished)
  }

  private companion object {
    const val GLOB_CHARACTERS = "*?[\\"
    const val SCAN_COUNT = 1000L
    const val DEL_CHUNK = 1000
  }
}

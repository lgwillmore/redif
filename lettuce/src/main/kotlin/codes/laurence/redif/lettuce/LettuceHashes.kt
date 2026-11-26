package codes.laurence.redif.lettuce

import codes.laurence.redif.commands.hashes.HashesAPI
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.flow.toList

@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class LettuceHashes(connection: LettuceConnection) : HashesAPI {

  private val commands = connection.commands

  override suspend fun hset(key: String, fields: Map<String, String>): Long {
    if (fields.isEmpty()) return 0
    return redis { commands.hset(key, fields).check() }
  }

  override suspend fun hget(key: String, field: String): String? = redis {
    commands.hget(key, field)
  }

  override suspend fun hmget(key: String, vararg fields: String): List<String?> {
    if (fields.isEmpty()) return emptyList()
    return redis { commands.hmget(key, *fields).toList() }
        .map { if (it.hasValue()) it.value else null }
  }

  override suspend fun hgetall(key: String): Map<String, String> =
      redis { commands.hgetall(key).toList() }.associate { it.key to it.value }

  override suspend fun hdel(key: String, vararg fields: String): Long {
    if (fields.isEmpty()) return 0
    return redis { commands.hdel(key, *fields).check() }
  }

  override suspend fun hexists(key: String, field: String): Boolean = redis {
    commands.hexists(key, field).check()
  }

  override suspend fun hlen(key: String): Long = redis { commands.hlen(key).check() }

  override suspend fun hincrby(key: String, field: String, by: Long): Long = redis {
    commands.hincrby(key, field, by).check()
  }
}

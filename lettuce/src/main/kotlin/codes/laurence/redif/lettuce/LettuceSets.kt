package codes.laurence.redif.lettuce

import codes.laurence.redif.commands.sets.SetsAPI
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import kotlinx.coroutines.flow.toSet

@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class LettuceSets(connection: LettuceConnection) : SetsAPI {

  private val commands = connection.commands

  override suspend fun sadd(key: String, vararg members: String): Long {
    if (members.isEmpty()) return 0
    return redis { commands.sadd(key, *members).check() }
  }

  override suspend fun srem(key: String, vararg members: String): Long {
    if (members.isEmpty()) return 0
    return redis { commands.srem(key, *members).check() }
  }

  override suspend fun scard(key: String): Long = redis { commands.scard(key).check() }

  override suspend fun sismember(key: String, member: String): Boolean = redis {
    commands.sismember(key, member).check()
  }

  override suspend fun smembers(key: String): Set<String> = redis { commands.smembers(key).toSet() }
}

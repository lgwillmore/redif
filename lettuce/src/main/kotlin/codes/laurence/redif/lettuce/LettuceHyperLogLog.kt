package codes.laurence.redif.lettuce

import codes.laurence.redif.commands.hyperloglog.HyperLogLogAPI
import io.lettuce.core.ExperimentalLettuceCoroutinesApi

@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class LettuceHyperLogLog(connection: LettuceConnection) : HyperLogLogAPI {

  private val commands = connection.commands

  override suspend fun pfadd(key: String, vararg elements: String): Boolean = redis {
    commands.pfadd(key, *elements).check() == 1L
  }

  override suspend fun pfcount(vararg keys: String): Long {
    if (keys.isEmpty()) return 0
    return redis { commands.pfcount(*keys).check() }
  }

  override suspend fun pfmerge(destKey: String, vararg sourceKeys: String) {
    redis { commands.pfmerge(destKey, *sourceKeys) }
  }
}

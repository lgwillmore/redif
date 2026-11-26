package codes.laurence.redif.lettuce

import codes.laurence.redif.commands.hyperloglog.HyperLogLogAPI
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.codec.StringCodec
import io.lettuce.core.output.IntegerOutput
import io.lettuce.core.output.StatusOutput
import io.lettuce.core.protocol.CommandArgs
import io.lettuce.core.protocol.CommandType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first

@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class LettuceHyperLogLog(connection: LettuceConnection) : HyperLogLogAPI {

  private val commands = connection.commands

  // Redis takes PFADD and PFMERGE with a key alone, to create it, where Lettuce's own methods
  // insist on an element or a source; those two go to Redis as the bare command.
  private fun keyAlone(key: String) = CommandArgs(StringCodec.UTF8).addKey(key)

  override suspend fun pfadd(key: String, vararg elements: String): Boolean = redis {
    val changed =
        if (elements.isEmpty()) {
          commands
              .dispatch(CommandType.PFADD, IntegerOutput(StringCodec.UTF8), keyAlone(key))
              .first()
        } else {
          commands.pfadd(key, *elements).check()
        }
    changed == 1L
  }

  override suspend fun pfcount(vararg keys: String): Long {
    if (keys.isEmpty()) return 0
    return redis { commands.pfcount(*keys).check() }
  }

  override suspend fun pfmerge(destKey: String, vararg sourceKeys: String) {
    redis {
      if (sourceKeys.isEmpty()) {
        commands
            .dispatch(CommandType.PFMERGE, StatusOutput(StringCodec.UTF8), keyAlone(destKey))
            .collect()
      } else {
        commands.pfmerge(destKey, *sourceKeys)
      }
    }
  }
}

package codes.laurence.redif.lettuce

import codes.laurence.redif.commands.strings.SetArgs
import codes.laurence.redif.commands.strings.SetCondition
import codes.laurence.redif.commands.strings.StringsAPI
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.SetArgs as LettuceSetArgs
import kotlinx.coroutines.flow.toList

@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class LettuceStrings(connection: LettuceConnection) : StringsAPI {

  private val commands = connection.commands

  override suspend fun set(key: String, value: String) {
    redis { commands.set(key, value) }
  }

  override suspend fun set(key: String, value: String, args: SetArgs): String? {
    val lettuceArgs = LettuceSetArgs()
    when (args.condition) {
      SetCondition.NX -> lettuceArgs.nx()
      SetCondition.XX -> lettuceArgs.xx()
      null -> {}
    }
    args.pxMillis?.let { lettuceArgs.px(it) }
    if (args.keepTtl) lettuceArgs.keepttl()
    return redis {
      if (args.get) commands.setGet(key, value, lettuceArgs)
      else commands.set(key, value, lettuceArgs)
    }
  }

  override suspend fun psetx(key: String, value: String, expireMillis: Long) {
    redis { commands.set(key, value, LettuceSetArgs.Builder.px(expireMillis)) }
  }

  // SET NX answers OK when it set and nothing when the key was already there.
  override suspend fun setnx(key: String, value: String): Boolean = redis {
    commands.set(key, value, LettuceSetArgs.Builder.nx()) != null
  }

  override suspend fun get(key: String): String? = redis { commands.get(key) }

  override suspend fun mget(vararg keys: String): List<String?> {
    if (keys.isEmpty()) return emptyList()
    return redis { commands.mget(*keys).toList() }.map { if (it.hasValue()) it.value else null }
  }

  override suspend fun mset(values: Map<String, String>) {
    if (values.isEmpty()) return
    redis { commands.mset(values) }
  }

  override suspend fun getdel(key: String): String? = redis { commands.getdel(key) }

  override suspend fun incrby(key: String, by: Long): Long = redis {
    commands.incrby(key, by).check()
  }

  override suspend fun decrby(key: String, by: Long): Long = redis {
    commands.decrby(key, by).check()
  }
}

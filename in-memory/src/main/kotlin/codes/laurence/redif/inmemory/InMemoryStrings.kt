package codes.laurence.redif.inmemory

import codes.laurence.redif.commands.strings.SetArgs
import codes.laurence.redif.commands.strings.SetCondition
import codes.laurence.redif.commands.strings.StringsAPI

internal class InMemoryStrings(private val keyspace: Keyspace) : StringsAPI {

  override suspend fun set(key: String, value: String) =
      keyspace.locked { put(key, StringValue(value)) }

  override suspend fun set(key: String, value: String, args: SetArgs): String? =
      keyspace.locked {
        val existing = find(key)
        val old = if (args.get) typed<StringValue>(key)?.text else null
        val allowed =
            when (args.condition) {
              SetCondition.NX -> existing == null
              SetCondition.XX -> existing != null
              null -> true
            }
        if (allowed) {
          val expireAt =
              when {
                args.pxMillis != null -> after(args.pxMillis!!)
                args.keepTtl -> existing?.expireAt
                else -> null
              }
          put(key, StringValue(value), expireAt)
        }
        when {
          args.get -> old
          allowed -> "OK"
          else -> null
        }
      }

  override suspend fun psetx(key: String, value: String, expireMillis: Long) =
      keyspace.locked { put(key, StringValue(value), after(expireMillis)) }

  override suspend fun setnx(key: String, value: String): Boolean =
      keyspace.locked {
        if (find(key) == null) {
          put(key, StringValue(value))
          true
        } else {
          false
        }
      }

  override suspend fun get(key: String): String? = keyspace.locked { typed<StringValue>(key)?.text }

  override suspend fun mget(vararg keys: String): List<String?> =
      keyspace.locked { keys.map { (find(it)?.value as? StringValue)?.text } }

  override suspend fun mset(values: Map<String, String>) =
      keyspace.locked { values.forEach { (key, value) -> put(key, StringValue(value)) } }

  override suspend fun getdel(key: String): String? =
      keyspace.locked { typed<StringValue>(key)?.text?.also { remove(key) } }

  override suspend fun incrby(key: String, by: Long): Long =
      keyspace.locked {
        val existing = typed<StringValue>(key)
        val next = addTo(existing?.text, by, "value is not an integer or out of range")
        // The key keeps its expiry.
        if (existing == null) put(key, StringValue(next.toString())) else existing.text = "$next"
        next
      }

  override suspend fun decrby(key: String, by: Long): Long {
    if (by == Long.MIN_VALUE) throw err("decrement would overflow")
    return incrby(key, -by)
  }
}

/** [by] added to the integer [text] holds, zero when null, with Redis's errors for the rest. */
internal fun addTo(text: String?, by: Long, notAnInteger: String): Long {
  val current =
      if (text == null) 0L
      else text.takeUnless { it.startsWith("+") }?.toLongOrNull() ?: throw err(notAnInteger)
  val next = current + by
  // Overflow is the one case where the sum's sign differs from both of its terms.
  if (((current xor next) and (by xor next)) < 0) {
    throw err("increment or decrement would overflow")
  }
  return next
}

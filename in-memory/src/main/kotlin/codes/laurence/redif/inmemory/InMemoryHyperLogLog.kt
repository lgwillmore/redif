package codes.laurence.redif.inmemory

import codes.laurence.redif.commands.hyperloglog.HyperLogLogAPI

internal class InMemoryHyperLogLog(private val keyspace: Keyspace) : HyperLogLogAPI {

  override suspend fun pfadd(key: String, vararg elements: String): Boolean =
      keyspace.locked {
        val existing = typed<HyperLogLogValue>(key)
        val log = existing ?: typedOrPut(key) { HyperLogLogValue() }
        // Creating the key is itself a change, as in Redis.
        var changed = existing == null
        elements.forEach { if (log.elements.add(it)) changed = true }
        changed
      }

  override suspend fun pfcount(vararg keys: String): Long =
      keyspace.locked {
        val union = HashSet<String>()
        keys.forEach { key -> typed<HyperLogLogValue>(key)?.let { union.addAll(it.elements) } }
        union.size.toLong()
      }

  override suspend fun pfmerge(destKey: String, vararg sourceKeys: String) =
      keyspace.locked {
        val sources = sourceKeys.mapNotNull { typed<HyperLogLogValue>(it) }
        val dest = typedOrPut(destKey) { HyperLogLogValue() }
        sources.forEach { dest.elements.addAll(it.elements) }
      }
}

package codes.laurence.redif.inmemory

import codes.laurence.redif.commands.sets.SetsAPI

internal class InMemorySets(private val keyspace: Keyspace) : SetsAPI {

  override suspend fun sadd(key: String, vararg members: String): Long =
      keyspace.locked {
        if (members.isEmpty()) return@locked 0L
        val set = typedOrPut(key) { SetValue() }
        members.count { set.members.add(it) }.toLong()
      }

  override suspend fun srem(key: String, vararg members: String): Long =
      keyspace.locked {
        val set = typed<SetValue>(key) ?: return@locked 0L
        val count = members.count { set.members.remove(it) }.toLong()
        removeIfEmpty(key, set)
        count
      }

  override suspend fun scard(key: String): Long =
      keyspace.locked { typed<SetValue>(key)?.members?.size?.toLong() ?: 0L }

  override suspend fun sismember(key: String, member: String): Boolean =
      keyspace.locked { typed<SetValue>(key)?.members?.contains(member) ?: false }

  override suspend fun smembers(key: String): Set<String> =
      keyspace.locked { typed<SetValue>(key)?.members?.toSet() ?: emptySet() }
}

package codes.laurence.redif.inmemory

import codes.laurence.redif.commands.hashes.HashesAPI

internal class InMemoryHashes(private val keyspace: Keyspace) : HashesAPI {

  override suspend fun hset(key: String, fields: Map<String, String>): Long =
      keyspace.locked {
        if (fields.isEmpty()) return@locked 0L
        val hash = typedOrPut(key) { HashValue() }
        fields.count { (field, value) -> hash.fields.put(field, value) == null }.toLong()
      }

  override suspend fun hget(key: String, field: String): String? =
      keyspace.locked { typed<HashValue>(key)?.fields?.get(field) }

  override suspend fun hmget(key: String, vararg fields: String): List<String?> =
      keyspace.locked {
        val hash = typed<HashValue>(key)
        fields.map { hash?.fields?.get(it) }
      }

  override suspend fun hgetall(key: String): Map<String, String> =
      keyspace.locked { typed<HashValue>(key)?.fields?.toMap() ?: emptyMap() }

  override suspend fun hdel(key: String, vararg fields: String): Long =
      keyspace.locked {
        val hash = typed<HashValue>(key) ?: return@locked 0L
        val count = fields.count { hash.fields.remove(it) != null }.toLong()
        removeIfEmpty(key, hash)
        count
      }

  override suspend fun hexists(key: String, field: String): Boolean =
      keyspace.locked { typed<HashValue>(key)?.fields?.containsKey(field) ?: false }

  override suspend fun hlen(key: String): Long =
      keyspace.locked { typed<HashValue>(key)?.fields?.size?.toLong() ?: 0L }

  override suspend fun hincrby(key: String, field: String, by: Long): Long =
      keyspace.locked {
        val existing = typed<HashValue>(key)
        val next = addTo(existing?.fields?.get(field), by, "hash value is not an integer")
        (existing ?: typedOrPut(key) { HashValue() }).fields[field] = next.toString()
        next
      }
}

package codes.laurence.redif.commands.sets

interface SetsAPI {
  /** Answers how many members were new. */
  suspend fun sadd(key: String, vararg members: String): Long

  suspend fun srem(key: String, vararg members: String): Long

  suspend fun scard(key: String): Long

  suspend fun sismember(key: String, member: String): Boolean

  suspend fun smembers(key: String): Set<String>
}

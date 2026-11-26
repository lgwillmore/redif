package codes.laurence.redif.commands.hashes

interface HashesAPI {
  /** Answers how many fields were new. */
  suspend fun hset(key: String, fields: Map<String, String>): Long

  suspend fun hget(key: String, field: String): String?

  /** In field order, with null for a field that is missing. */
  suspend fun hmget(key: String, vararg fields: String): List<String?>

  suspend fun hgetall(key: String): Map<String, String>

  suspend fun hdel(key: String, vararg fields: String): Long

  suspend fun hexists(key: String, field: String): Boolean

  suspend fun hlen(key: String): Long

  /** A missing field counts from zero. An `ERR` when the field's value is not an integer. */
  suspend fun hincrby(key: String, field: String, by: Long): Long
}

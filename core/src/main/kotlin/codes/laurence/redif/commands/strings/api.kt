package codes.laurence.redif.commands.strings

interface StringsAPI {
  suspend fun set(key: String, value: String)

  /**
   * `SET` with options. Answers `"OK"` when it set and null when [SetArgs.condition] stopped it;
   * with [SetArgs.get] it answers the value the key held before instead, null when it held none.
   */
  suspend fun set(key: String, value: String, args: SetArgs): String?

  suspend fun psetx(key: String, value: String, expireMillis: Long)

  suspend fun setnx(key: String, value: String): Boolean

  suspend fun get(key: String): String?

  /** In key order, with null for a key that is missing or holds another type. */
  suspend fun mget(vararg keys: String): List<String?>

  suspend fun mset(values: Map<String, String>)

  suspend fun getdel(key: String): String?

  /** A missing key counts from zero. An `ERR` when the value is not an integer. */
  suspend fun incrby(key: String, by: Long): Long

  suspend fun decrby(key: String, by: Long): Long
}

package codes.laurence.redif.commands.sortedsets

interface SortedSetsAPI {
  /** Answers how many members were new, or with [ZAddArgs.ch] how many were new or rescored. */
  suspend fun zadd(key: String, members: Map<String, Double>, args: ZAddArgs? = null): Long

  suspend fun zrem(key: String, vararg members: String): Long

  suspend fun zcard(key: String): Long

  suspend fun zscore(key: String, member: String): Double?

  /** A missing member counts from zero. Answers the new score. */
  suspend fun zincrby(key: String, by: Double, member: String): Double

  suspend fun zcount(key: String, min: ScoreBound, max: ScoreBound): Long

  /**
   * `ZRANGE … WITHSCORES`, by rank or by score. Members come lowest score first, those with equal
   * scores ordered by member byte-wise, or the reverse of that when the range says `rev`.
   */
  suspend fun zrange(key: String, range: ZRange): List<ScoredMember>

  suspend fun zremrangebyscore(key: String, min: ScoreBound, max: ScoreBound): Long
}

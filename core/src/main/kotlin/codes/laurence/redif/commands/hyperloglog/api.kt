package codes.laurence.redif.commands.hyperloglog

interface HyperLogLogAPI {
  /** Answers whether the estimate changed. */
  suspend fun pfadd(key: String, vararg elements: String): Boolean

  /**
   * An estimate of the distinct elements added across [keys], with the standard error Redis states
   * for a HyperLogLog (0.81%). An implementation may be more exact than that, never less.
   */
  suspend fun pfcount(vararg keys: String): Long

  /** Makes [destKey] the union of itself and [sourceKeys]. */
  suspend fun pfmerge(destKey: String, vararg sourceKeys: String)
}

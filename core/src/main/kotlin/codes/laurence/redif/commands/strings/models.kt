package codes.laurence.redif.commands.strings

/**
 * The options of `SET`.
 *
 * @property condition `NX` or `XX`; null sets always.
 * @property pxMillis `PX`: the key's expiry. Without it, and without [keepTtl], the key is left
 *   with no expiry.
 * @property keepTtl `KEEPTTL`: keep the expiry the key already has.
 * @property get `GET`: answer the value the key held before.
 */
data class SetArgs(
    val condition: SetCondition? = null,
    val pxMillis: Long? = null,
    val keepTtl: Boolean = false,
    val get: Boolean = false,
) {
  init {
    require(pxMillis == null || pxMillis > 0) { "PX must be positive" }
    require(pxMillis == null || !keepTtl) { "PX and KEEPTTL cannot be given together" }
  }
}

enum class SetCondition {
  /** Set only when the key does not exist. */
  NX,

  /** Set only when the key exists. */
  XX,
}

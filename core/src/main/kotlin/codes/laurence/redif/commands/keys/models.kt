package codes.laurence.redif.commands.keys

/** When `PEXPIRE` may set the expiry. A key with no expiry counts as one that never expires. */
enum class ExpireCondition {
  /** Only when the key has no expiry. */
  NX,

  /** Only when the key has an expiry. */
  XX,

  /** Only when the new expiry is later than the one it has. */
  GT,

  /** Only when the new expiry is sooner than the one it has. */
  LT,
}

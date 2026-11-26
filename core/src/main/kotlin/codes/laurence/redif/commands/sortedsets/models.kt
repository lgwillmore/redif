package codes.laurence.redif.commands.sortedsets

data class ScoredMember(val member: String, val score: Double)

/**
 * The options of `ZADD`.
 *
 * @property condition `NX` or `XX`; null both adds and rescores.
 * @property comparison `GT` or `LT`: when an existing member may be rescored. Neither stops a new
 *   member being added.
 * @property ch `CH`: count the members rescored as well as those added.
 */
data class ZAddArgs(
    val condition: ZAddCondition? = null,
    val comparison: ZAddComparison? = null,
    val ch: Boolean = false,
) {
  init {
    require(condition != ZAddCondition.NX || comparison == null) {
      "NX cannot be given with GT or LT"
    }
  }
}

enum class ZAddCondition {
  /** Only add new members; never rescore one. */
  NX,

  /** Only rescore existing members; never add one. */
  XX,
}

enum class ZAddComparison {
  /** Rescore a member only to a greater score. */
  GT,

  /** Rescore a member only to a lesser score. */
  LT,
}

/**
 * One end of a range of scores: Redis's `1.5`, `(1.5`, `-inf` and `+inf`. A bound with a score
 * takes a finite one; the infinite bounds include members scored infinite.
 */
sealed interface ScoreBound {
  data class Inclusive(val score: Double) : ScoreBound {
    init {
      require(score.isFinite()) { "Use NegativeInfinity or PositiveInfinity" }
    }
  }

  data class Exclusive(val score: Double) : ScoreBound {
    init {
      require(score.isFinite()) { "Use NegativeInfinity or PositiveInfinity" }
    }
  }

  data object NegativeInfinity : ScoreBound

  data object PositiveInfinity : ScoreBound
}

/** What `ZRANGE` selects. */
sealed interface ZRange {
  /**
   * By position, from zero, both ends included; a negative position counts back from the end, so
   * `ByRank(0, -1)` is everything. With [rev] positions count from the highest score.
   */
  data class ByRank(val start: Long, val stop: Long, val rev: Boolean = false) : ZRange

  /**
   * `BYSCORE`. [min] is always the lower bound and [max] the upper, whichever way the members come
   * back; [rev] returns them highest score first, and [limit] is applied in that order.
   */
  data class ByScore(
      val min: ScoreBound,
      val max: ScoreBound,
      val rev: Boolean = false,
      val limit: ZLimit? = null,
  ) : ZRange
}

/** `LIMIT offset count`. A negative [count] takes everything from [offset]. */
data class ZLimit(val offset: Long, val count: Long)

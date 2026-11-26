package codes.laurence.redif.lettuce

import codes.laurence.redif.commands.sortedsets.ScoreBound
import codes.laurence.redif.commands.sortedsets.ScoredMember
import codes.laurence.redif.commands.sortedsets.SortedSetsAPI
import codes.laurence.redif.commands.sortedsets.ZAddArgs
import codes.laurence.redif.commands.sortedsets.ZAddComparison
import codes.laurence.redif.commands.sortedsets.ZAddCondition
import codes.laurence.redif.commands.sortedsets.ZRange
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.Range
import io.lettuce.core.ScoredValue
import io.lettuce.core.ZAddArgs as LettuceZAddArgs
import io.lettuce.core.ZRange as LettuceZRange
import kotlinx.coroutines.flow.toList

@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class LettuceSortedSets(connection: LettuceConnection) : SortedSetsAPI {

  private val commands = connection.commands

  override suspend fun zadd(key: String, members: Map<String, Double>, args: ZAddArgs?): Long {
    if (members.isEmpty()) return 0
    val scored = members.map { (member, score) -> ScoredValue.just(score, member) }.toTypedArray()
    if (args == null) return redis { commands.zadd(key, *scored).check() }
    val lettuceArgs = LettuceZAddArgs()
    when (args.condition) {
      ZAddCondition.NX -> lettuceArgs.nx()
      ZAddCondition.XX -> lettuceArgs.xx()
      null -> {}
    }
    when (args.comparison) {
      ZAddComparison.GT -> lettuceArgs.gt()
      ZAddComparison.LT -> lettuceArgs.lt()
      null -> {}
    }
    if (args.ch) lettuceArgs.ch()
    return redis { commands.zadd(key, lettuceArgs, *scored).check() }
  }

  override suspend fun zrem(key: String, vararg members: String): Long {
    if (members.isEmpty()) return 0
    return redis { commands.zrem(key, *members).check() }
  }

  override suspend fun zcard(key: String): Long = redis { commands.zcard(key).check() }

  override suspend fun zscore(key: String, member: String): Double? = redis {
    commands.zscore(key, member)
  }

  override suspend fun zincrby(key: String, by: Double, member: String): Double = redis {
    commands.zincrby(key, by, member).check()
  }

  override suspend fun zcount(key: String, min: ScoreBound, max: ScoreBound): Long = redis {
    commands.zcount(key, scoreRange(min, max)).check()
  }

  override suspend fun zrange(key: String, range: ZRange): List<ScoredMember> {
    val scored = redis {
      when (range) {
        is ZRange.ByRank -> {
          val byIndex = LettuceZRange.byIndex(range.start, range.stop)
          commands.zrangeWithScores(key, if (range.rev) byIndex.rev() else byIndex).toList()
        }
        is ZRange.ByScore -> {
          var byScore = LettuceZRange.byScore(scoreRange(range.min, range.max))
          if (range.rev) byScore = byScore.rev()
          range.limit?.let { byScore = byScore.limit(it.offset, it.count) }
          commands.zrangeWithScores(key, byScore).toList()
        }
      }
    }
    return scored.map { ScoredMember(it.value, it.score) }
  }

  override suspend fun zremrangebyscore(key: String, min: ScoreBound, max: ScoreBound): Long =
      redis {
        commands.zremrangebyscore(key, scoreRange(min, max)).check()
      }

  private fun scoreRange(min: ScoreBound, max: ScoreBound): Range<Double> =
      Range.from(min.boundary(), max.boundary())

  private fun ScoreBound.boundary(): Range.Boundary<Double> =
      when (this) {
        is ScoreBound.Inclusive -> Range.Boundary.including(score)
        is ScoreBound.Exclusive -> Range.Boundary.excluding(score)
        ScoreBound.NegativeInfinity -> Range.Boundary.including(Double.NEGATIVE_INFINITY)
        ScoreBound.PositiveInfinity -> Range.Boundary.including(Double.POSITIVE_INFINITY)
      }
}

package codes.laurence.redif.inmemory

import codes.laurence.redif.commands.sortedsets.ScoreBound
import codes.laurence.redif.commands.sortedsets.ScoredMember
import codes.laurence.redif.commands.sortedsets.SortedSetsAPI
import codes.laurence.redif.commands.sortedsets.ZAddArgs
import codes.laurence.redif.commands.sortedsets.ZAddComparison
import codes.laurence.redif.commands.sortedsets.ZAddCondition
import codes.laurence.redif.commands.sortedsets.ZRange

internal class InMemorySortedSets(private val keyspace: Keyspace) : SortedSetsAPI {

  override suspend fun zadd(key: String, members: Map<String, Double>, args: ZAddArgs?): Long =
      keyspace.locked {
        if (members.isEmpty()) return@locked 0L
        if (members.values.any { it.isNaN() }) throw err("value is not a valid float")
        val zset = typedOrPut(key) { SortedSetValue() }
        var added = 0L
        var changed = 0L
        members.forEach { (member, score) ->
          val current = zset.scores[member]
          if (current == null) {
            if (args?.condition != ZAddCondition.XX) {
              zset.put(member, score)
              added++
            }
          } else {
            val allowed =
                args?.condition != ZAddCondition.NX &&
                    when (args?.comparison) {
                      ZAddComparison.GT -> score > current
                      ZAddComparison.LT -> score < current
                      null -> true
                    }
            if (allowed && score != current) {
              zset.put(member, score)
              changed++
            }
          }
        }
        removeIfEmpty(key, zset)
        if (args?.ch == true) added + changed else added
      }

  override suspend fun zrem(key: String, vararg members: String): Long =
      keyspace.locked {
        val zset = typed<SortedSetValue>(key) ?: return@locked 0L
        val count = members.count { zset.drop(it) }.toLong()
        removeIfEmpty(key, zset)
        count
      }

  override suspend fun zcard(key: String): Long =
      keyspace.locked { typed<SortedSetValue>(key)?.scores?.size?.toLong() ?: 0L }

  override suspend fun zscore(key: String, member: String): Double? =
      keyspace.locked { typed<SortedSetValue>(key)?.scores?.get(member) }

  override suspend fun zincrby(key: String, by: Double, member: String): Double =
      keyspace.locked {
        val existing = typed<SortedSetValue>(key)
        val next = (existing?.scores?.get(member) ?: 0.0) + by
        if (next.isNaN()) throw err("resulting score is not a number (NaN)")
        (existing ?: typedOrPut(key) { SortedSetValue() }).put(member, next)
        next
      }

  override suspend fun zcount(key: String, min: ScoreBound, max: ScoreBound): Long =
      keyspace.locked {
        typed<SortedSetValue>(key)?.ordered?.count { it.score.within(min, max) }?.toLong() ?: 0L
      }

  override suspend fun zrange(key: String, range: ZRange): List<ScoredMember> =
      keyspace.locked {
        val ordered = typed<SortedSetValue>(key)?.ordered?.toList() ?: return@locked emptyList()
        val selected =
            when (range) {
              is ZRange.ByRank -> {
                val all = if (range.rev) ordered.reversed() else ordered
                val start = (if (range.start < 0) all.size + range.start else range.start)
                val stop = (if (range.stop < 0) all.size + range.stop else range.stop)
                val from = start.coerceAtLeast(0)
                val to = stop.coerceAtMost(all.size - 1L)
                if (from > to) emptyList() else all.subList(from.toInt(), to.toInt() + 1)
              }
              is ZRange.ByScore -> {
                val within = ordered.filter { it.score.within(range.min, range.max) }
                val all = if (range.rev) within.reversed() else within
                val limit = range.limit
                when {
                  limit == null -> all
                  limit.offset < 0 -> emptyList()
                  limit.count < 0 -> all.drop(limit.offset.toIntSaturated())
                  else -> all.drop(limit.offset.toIntSaturated()).take(limit.count.toIntSaturated())
                }
              }
            }
        selected.map { ScoredMember(it.member, it.score) }
      }

  override suspend fun zremrangebyscore(key: String, min: ScoreBound, max: ScoreBound): Long =
      keyspace.locked {
        val zset = typed<SortedSetValue>(key) ?: return@locked 0L
        val going = zset.ordered.filter { it.score.within(min, max) }
        going.forEach { zset.drop(it.member) }
        removeIfEmpty(key, zset)
        going.size.toLong()
      }
}

private fun Double.within(min: ScoreBound, max: ScoreBound): Boolean {
  val aboveMin =
      when (min) {
        is ScoreBound.Inclusive -> this >= min.score
        is ScoreBound.Exclusive -> this > min.score
        ScoreBound.NegativeInfinity -> true
        ScoreBound.PositiveInfinity -> this == Double.POSITIVE_INFINITY
      }
  val belowMax =
      when (max) {
        is ScoreBound.Inclusive -> this <= max.score
        is ScoreBound.Exclusive -> this < max.score
        ScoreBound.NegativeInfinity -> this == Double.NEGATIVE_INFINITY
        ScoreBound.PositiveInfinity -> true
      }
  return aboveMin && belowMax
}

internal fun Long.toIntSaturated(): Int = coerceIn(0, Int.MAX_VALUE.toLong()).toInt()

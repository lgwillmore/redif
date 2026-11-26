package codes.laurence.redif.compatibility

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import codes.laurence.redif.RedifCommandException
import codes.laurence.redif.commands.sortedsets.ScoreBound
import codes.laurence.redif.commands.sortedsets.ScoreBound.Exclusive
import codes.laurence.redif.commands.sortedsets.ScoreBound.Inclusive
import codes.laurence.redif.commands.sortedsets.ScoreBound.NegativeInfinity
import codes.laurence.redif.commands.sortedsets.ScoreBound.PositiveInfinity
import codes.laurence.redif.commands.sortedsets.ScoredMember
import codes.laurence.redif.commands.sortedsets.ZAddArgs
import codes.laurence.redif.commands.sortedsets.ZAddComparison
import codes.laurence.redif.commands.sortedsets.ZAddCondition
import codes.laurence.redif.commands.sortedsets.ZLimit
import codes.laurence.redif.commands.sortedsets.ZRange
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/** The cases for `SortedSetsAPI`. */
abstract class SortedSetsContract(harness: RedifHarness) : RedifContract(harness) {

  private val everything = ZRange.ByRank(0, -1)

  private suspend fun members(key: String, range: ZRange = everything): List<String> =
      client.zrange(key, range).map { it.member }

  /** A sorted set of `a` to `e`, scored 1 to 5. */
  private suspend fun oneToFive(): String {
    val key = randString()
    client.zadd(key, mapOf("c" to 3.0, "a" to 1.0, "e" to 5.0, "b" to 2.0, "d" to 4.0))
    return key
  }

  @Test
  fun `zadd - answers how many members were new`() {
    runBlocking {
      val key = randString()

      assertThat(client.zadd(key, mapOf("a" to 1.0, "b" to 2.0))).isEqualTo(2)
      assertThat(client.zadd(key, mapOf("b" to 20.0, "c" to 3.0))).isEqualTo(1)

      assertThat(client.zrange(key, everything))
          .containsExactly(ScoredMember("a", 1.0), ScoredMember("c", 3.0), ScoredMember("b", 20.0))
      assertThat(client.zcard(key)).isEqualTo(3)
    }
  }

  @Test
  fun `zadd - no members`() {
    runBlocking {
      val key = randString()

      assertThat(client.zadd(key, emptyMap())).isEqualTo(0)

      assertThat(client.exists(key)).isEqualTo(0)
    }
  }

  @Test
  fun `zadd - NX only adds`() {
    runBlocking {
      val key = randString()
      client.zadd(key, mapOf("a" to 1.0))

      val added = client.zadd(key, mapOf("a" to 10.0, "b" to 2.0), ZAddArgs(ZAddCondition.NX))

      assertThat(added).isEqualTo(1)
      assertThat(client.zscore(key, "a")).isEqualTo(1.0)
      assertThat(client.zscore(key, "b")).isEqualTo(2.0)
    }
  }

  @Test
  fun `zadd - XX only rescores`() {
    runBlocking {
      val key = randString()
      client.zadd(key, mapOf("a" to 1.0))

      val added = client.zadd(key, mapOf("a" to 10.0, "b" to 2.0), ZAddArgs(ZAddCondition.XX))

      assertThat(added).isEqualTo(0)
      assertThat(client.zscore(key, "a")).isEqualTo(10.0)
      assertThat(client.zscore(key, "b")).isNull()
    }
  }

  @Test
  fun `zadd - XX on a missing key creates nothing`() {
    runBlocking {
      val key = randString()

      client.zadd(key, mapOf("a" to 1.0), ZAddArgs(ZAddCondition.XX))

      assertThat(client.exists(key)).isEqualTo(0)
    }
  }

  @Test
  fun `zadd - GT rescores only upwards and still adds`() {
    runBlocking {
      val key = randString()
      client.zadd(key, mapOf("up" to 5.0, "down" to 5.0))
      val args = ZAddArgs(comparison = ZAddComparison.GT)

      val added = client.zadd(key, mapOf("up" to 9.0, "down" to 1.0, "new" to 3.0), args)

      assertThat(added).isEqualTo(1)
      assertThat(client.zscore(key, "up")).isEqualTo(9.0)
      assertThat(client.zscore(key, "down")).isEqualTo(5.0)
      assertThat(client.zscore(key, "new")).isEqualTo(3.0)
    }
  }

  @Test
  fun `zadd - LT rescores only downwards`() {
    runBlocking {
      val key = randString()
      client.zadd(key, mapOf("up" to 5.0, "down" to 5.0))
      val args = ZAddArgs(comparison = ZAddComparison.LT)

      client.zadd(key, mapOf("up" to 9.0, "down" to 1.0), args)

      assertThat(client.zscore(key, "up")).isEqualTo(5.0)
      assertThat(client.zscore(key, "down")).isEqualTo(1.0)
    }
  }

  @Test
  fun `zadd - XX with GT`() {
    runBlocking {
      val key = randString()
      client.zadd(key, mapOf("a" to 5.0))
      val args = ZAddArgs(ZAddCondition.XX, ZAddComparison.GT)

      client.zadd(key, mapOf("a" to 9.0, "new" to 3.0), args)

      assertThat(client.zscore(key, "a")).isEqualTo(9.0)
      assertThat(client.zscore(key, "new")).isNull()
    }
  }

  @Test
  fun `zadd - CH counts the rescored as well`() {
    runBlocking {
      val key = randString()
      client.zadd(key, mapOf("same" to 1.0, "moved" to 2.0))

      val changed =
          client.zadd(key, mapOf("same" to 1.0, "moved" to 20.0, "new" to 3.0), ZAddArgs(ch = true))

      assertThat(changed).isEqualTo(2)
    }
  }

  @Test
  fun `zrem - answers how many members went`() {
    runBlocking {
      val key = oneToFive()

      assertThat(client.zrem(key, "a", "b", "missing")).isEqualTo(2)

      assertThat(members(key)).containsExactly("c", "d", "e")
      assertThat(client.zrem(randString(), "a")).isEqualTo(0)
    }
  }

  @Test
  fun `zrem - the last member removes the key`() {
    runBlocking {
      val key = randString()
      client.zadd(key, mapOf("a" to 1.0))

      client.zrem(key, "a")

      assertThat(client.exists(key)).isEqualTo(0)
    }
  }

  @Test
  fun `zscore - missing member and missing key`() {
    runBlocking {
      val key = oneToFive()

      assertThat(client.zscore(key, "missing")).isNull()
      assertThat(client.zscore(randString(), "a")).isNull()
    }
  }

  @Test
  fun `zincrby - a missing key and a missing member count from zero`() {
    runBlocking {
      val key = randString()

      assertThat(client.zincrby(key, 2.5, "a")).isEqualTo(2.5)
      assertThat(client.zincrby(key, -4.0, "a")).isEqualTo(-1.5)

      assertThat(client.zscore(key, "a")).isEqualTo(-1.5)
    }
  }

  @Test
  fun `zcount - bounds`() {
    runBlocking {
      val key = oneToFive()

      assertThat(client.zcount(key, Inclusive(2.0), Inclusive(4.0))).isEqualTo(3)
      assertThat(client.zcount(key, Exclusive(2.0), Inclusive(4.0))).isEqualTo(2)
      assertThat(client.zcount(key, Exclusive(2.0), Exclusive(4.0))).isEqualTo(1)
      assertThat(client.zcount(key, NegativeInfinity, Inclusive(2.0))).isEqualTo(2)
      assertThat(client.zcount(key, Exclusive(2.0), PositiveInfinity)).isEqualTo(3)
      assertThat(client.zcount(key, NegativeInfinity, PositiveInfinity)).isEqualTo(5)
      assertThat(client.zcount(key, Inclusive(4.0), Inclusive(2.0))).isEqualTo(0)
      assertThat(client.zcount(randString(), NegativeInfinity, PositiveInfinity)).isEqualTo(0)
    }
  }

  @Test
  fun `zrange by rank`() {
    runBlocking {
      val key = oneToFive()

      assertThat(members(key)).containsExactly("a", "b", "c", "d", "e")
      assertThat(members(key, ZRange.ByRank(1, 2))).containsExactly("b", "c")
      assertThat(members(key, ZRange.ByRank(-2, -1))).containsExactly("d", "e")
      assertThat(members(key, ZRange.ByRank(3, 100))).containsExactly("d", "e")
      assertThat(members(key, ZRange.ByRank(-100, 0))).containsExactly("a")
      assertThat(members(key, ZRange.ByRank(3, 1))).isEmpty()
      assertThat(members(key, ZRange.ByRank(5, 9))).isEmpty()
      assertThat(members(randString())).isEmpty()
    }
  }

  @Test
  fun `zrange by rank - rev`() {
    runBlocking {
      val key = oneToFive()

      assertThat(members(key, ZRange.ByRank(0, -1, rev = true)))
          .containsExactly("e", "d", "c", "b", "a")
      assertThat(members(key, ZRange.ByRank(0, 1, rev = true))).containsExactly("e", "d")
    }
  }

  @Test
  fun `zrange by score`() {
    runBlocking {
      val key = oneToFive()

      assertThat(members(key, ZRange.ByScore(Inclusive(2.0), Inclusive(4.0))))
          .containsExactly("b", "c", "d")
      assertThat(members(key, ZRange.ByScore(Exclusive(2.0), Exclusive(4.0)))).containsExactly("c")
      assertThat(members(key, ZRange.ByScore(NegativeInfinity, Inclusive(2.0))))
          .containsExactly("a", "b")
      assertThat(members(key, ZRange.ByScore(Inclusive(4.0), PositiveInfinity)))
          .containsExactly("d", "e")
      assertThat(members(key, ZRange.ByScore(Inclusive(6.0), PositiveInfinity))).isEmpty()
    }
  }

  @Test
  fun `zrange by score - rev keeps min and max`() {
    runBlocking {
      val key = oneToFive()

      assertThat(members(key, ZRange.ByScore(Inclusive(2.0), Exclusive(5.0), rev = true)))
          .containsExactly("d", "c", "b")
    }
  }

  @Test
  fun `zrange by score - limit`() {
    runBlocking {
      val key = oneToFive()
      val all: (ZLimit, Boolean) -> ZRange = { limit, rev ->
        ZRange.ByScore(NegativeInfinity, PositiveInfinity, rev = rev, limit = limit)
      }

      assertThat(members(key, all(ZLimit(1, 2), false))).containsExactly("b", "c")
      assertThat(members(key, all(ZLimit(1, 2), true))).containsExactly("d", "c")
      assertThat(members(key, all(ZLimit(3, -1), false))).containsExactly("d", "e")
      assertThat(members(key, all(ZLimit(9, 2), false))).isEmpty()
    }
  }

  @Test
  fun `zrange - equal scores order by member`() {
    runBlocking {
      val key = randString()
      client.zadd(key, mapOf("b" to 1.0, "B" to 1.0, "a" to 1.0, "ab" to 1.0, "0" to 2.0))

      assertThat(members(key)).containsExactly("B", "a", "ab", "b", "0")
    }
  }

  @Test
  fun `zrange - infinite scores`() {
    runBlocking {
      val key = randString()
      client.zadd(
          key,
          mapOf(
              "top" to Double.POSITIVE_INFINITY,
              "middle" to 0.0,
              "bottom" to Double.NEGATIVE_INFINITY,
          ),
      )

      assertThat(client.zrange(key, everything))
          .containsExactly(
              ScoredMember("bottom", Double.NEGATIVE_INFINITY),
              ScoredMember("middle", 0.0),
              ScoredMember("top", Double.POSITIVE_INFINITY),
          )
      assertThat(client.zcount(key, NegativeInfinity, PositiveInfinity)).isEqualTo(3)
      assertThat(client.zcount(key, NegativeInfinity, Inclusive(0.0))).isEqualTo(2)
      assertThat(client.zcount(key, Exclusive(0.0), PositiveInfinity)).isEqualTo(1)
    }
  }

  @Test
  fun `zremrangebyscore - answers how many members went`() {
    runBlocking {
      val key = oneToFive()

      assertThat(client.zremrangebyscore(key, NegativeInfinity, Exclusive(3.0))).isEqualTo(2)

      assertThat(members(key)).containsExactly("c", "d", "e")
      assertThat(client.zremrangebyscore(randString(), NegativeInfinity, PositiveInfinity))
          .isEqualTo(0)
    }
  }

  @Test
  fun `zremrangebyscore - removing everything removes the key`() {
    runBlocking {
      val key = oneToFive()

      assertThat(client.zremrangebyscore(key, NegativeInfinity, PositiveInfinity)).isEqualTo(5)

      assertThat(client.exists(key)).isEqualTo(0)
    }
  }

  @Test
  fun `live holds - trim the stale and count`() {
    runBlocking {
      // The score is a hold's last heartbeat: holds not heard from since the cutoff are dropped,
      // and what is left is the count. Trimming twice changes nothing, so the two need no atom.
      val key = randString()
      client.zadd(key, mapOf("stale" to 1_000.0, "live" to 61_000.0))
      client.zadd(key, mapOf("live" to 62_000.0, "newer" to 62_500.0))
      val cutoff: ScoreBound = Exclusive(2_000.0)

      client.zremrangebyscore(key, NegativeInfinity, cutoff)
      client.zremrangebyscore(key, NegativeInfinity, cutoff)

      assertThat(client.zcard(key)).isEqualTo(2)
      assertThat(members(key)).containsExactly("live", "newer")
    }
  }

  @Test
  fun `sorted set commands - key of another type`() {
    runBlocking {
      val key = randString()
      client.set(key, "text")

      val commands: List<suspend () -> Unit> =
          listOf(
              { client.zadd(key, mapOf("a" to 1.0)) },
              { client.zrem(key, "a") },
              { client.zcard(key) },
              { client.zscore(key, "a") },
              { client.zincrby(key, 1.0, "a") },
              { client.zcount(key, NegativeInfinity, PositiveInfinity) },
              { client.zrange(key, everything) },
              { client.zremrangebyscore(key, NegativeInfinity, PositiveInfinity) },
          )

      commands.forEach { command ->
        assertThat(errorCodeOf(command)).isEqualTo(RedifCommandException.WRONGTYPE)
      }
    }
  }
}

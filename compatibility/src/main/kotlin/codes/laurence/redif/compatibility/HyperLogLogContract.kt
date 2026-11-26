package codes.laurence.redif.compatibility

import assertk.assertThat
import assertk.assertions.isBetween
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import codes.laurence.redif.RedifCommandException
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/**
 * The cases for `HyperLogLogAPI`. A HyperLogLog estimates, so a count of more than a handful of
 * elements is asserted within a few times Redis's stated standard error (0.81%), never exactly.
 */
abstract class HyperLogLogContract(harness: RedifHarness) : RedifContract(harness) {

  private fun elements(prefix: String, range: IntRange) = range.map { "$prefix:$it" }.toTypedArray()

  private fun about(expected: Long) = (expected * 0.97).toLong()..(expected * 1.03).toLong()

  @Test
  fun `pfadd - answers whether the estimate changed`() {
    runBlocking {
      val key = randString()

      assertThat(client.pfadd(key, "a", "b", "c")).isTrue()
      assertThat(client.pfadd(key, "a", "b", "c")).isFalse()
      assertThat(client.pfadd(key, "d")).isTrue()

      assertThat(client.pfcount(key)).isEqualTo(4)
    }
  }

  @Test
  fun `pfcount - missing key`() {
    runBlocking { assertThat(client.pfcount(randString())).isEqualTo(0) }
  }

  @Test
  fun `pfcount - a few thousand elements, within the error`() {
    runBlocking {
      val key = randString()
      val prefix = randString()

      client.pfadd(key, *elements(prefix, 1..5000))
      // Adding them again changes nothing.
      client.pfadd(key, *elements(prefix, 1..5000))

      assertThat(client.pfcount(key)).isBetween(about(5000).first, about(5000).last)
    }
  }

  @Test
  fun `pfcount - several keys count their union`() {
    runBlocking {
      val one = randString()
      val two = randString()
      val prefix = randString()
      client.pfadd(one, *elements(prefix, 1..3000))
      client.pfadd(two, *elements(prefix, 2001..5000))

      assertThat(client.pfcount(one, two, randString()))
          .isBetween(about(5000).first, about(5000).last)
    }
  }

  @Test
  fun `pfmerge - the destination becomes the union`() {
    runBlocking {
      val one = randString()
      val two = randString()
      val dest = randString()
      val prefix = randString()
      client.pfadd(one, *elements(prefix, 1..3000))
      client.pfadd(two, *elements(prefix, 2001..5000))
      client.pfadd(dest, *elements(prefix, 4001..6000))

      client.pfmerge(dest, one, two)

      assertThat(client.pfcount(dest)).isBetween(about(6000).first, about(6000).last)
      // The sources are left as they were.
      assertThat(client.pfcount(one)).isBetween(about(3000).first, about(3000).last)
    }
  }

  @Test
  fun `pfmerge - into a missing key`() {
    runBlocking {
      val source = randString()
      val dest = randString()
      client.pfadd(source, "a", "b")

      client.pfmerge(dest, source)

      assertThat(client.pfcount(dest)).isEqualTo(2)
    }
  }

  @Test
  fun `HyperLogLog commands - key of another type`() {
    runBlocking {
      val text = randString()
      val set = randString()
      client.set(text, "text")
      client.sadd(set, "member")

      val commands: List<suspend () -> Unit> =
          listOf(
              { client.pfadd(text, "a") },
              { client.pfcount(text) },
              { client.pfmerge(randString(), text) },
              { client.pfadd(set, "a") },
              { client.pfcount(set) },
          )

      commands.forEach { command ->
        assertThat(errorCodeOf(command)).isEqualTo(RedifCommandException.WRONGTYPE)
      }
    }
  }
}

package codes.laurence.redif.compatibility

import assertk.assertThat
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import codes.laurence.redif.RedifCommandException
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/** The cases for `SetsAPI`. */
abstract class SetsContract(harness: RedifHarness) : RedifContract(harness) {

  @Test
  fun `sadd - answers how many members were new`() {
    runBlocking {
      val key = randString()

      assertThat(client.sadd(key, "a", "b", "a")).isEqualTo(2)
      assertThat(client.sadd(key, "b", "c")).isEqualTo(1)

      assertThat(client.smembers(key)).containsOnly("a", "b", "c")
      assertThat(client.scard(key)).isEqualTo(3)
    }
  }

  @Test
  fun `srem - answers how many members went`() {
    runBlocking {
      val key = randString()
      client.sadd(key, "a", "b", "c")

      assertThat(client.srem(key, "a", "b", "missing")).isEqualTo(2)

      assertThat(client.smembers(key)).containsOnly("c")
      assertThat(client.srem(randString(), "a")).isEqualTo(0)
    }
  }

  @Test
  fun `srem - the last member removes the key`() {
    runBlocking {
      val key = randString()
      client.sadd(key, "a")

      client.srem(key, "a")

      assertThat(client.exists(key)).isEqualTo(0)
    }
  }

  @Test
  fun `sismember`() {
    runBlocking {
      val key = randString()
      client.sadd(key, "a")

      assertThat(client.sismember(key, "a")).isTrue()
      assertThat(client.sismember(key, "b")).isFalse()
      assertThat(client.sismember(randString(), "a")).isFalse()
    }
  }

  @Test
  fun `missing key - empty`() {
    runBlocking {
      val key = randString()

      assertThat(client.smembers(key)).isEmpty()
      assertThat(client.scard(key)).isEqualTo(0)
    }
  }

  @Test
  fun `set commands - key of another type`() {
    runBlocking {
      val key = randString()
      client.set(key, "text")

      val commands: List<suspend () -> Unit> =
          listOf(
              { client.sadd(key, "a") },
              { client.srem(key, "a") },
              { client.scard(key) },
              { client.sismember(key, "a") },
              { client.smembers(key) },
          )

      commands.forEach { command ->
        assertThat(errorCodeOf(command)).isEqualTo(RedifCommandException.WRONGTYPE)
      }
    }
  }
}

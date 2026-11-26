package codes.laurence.redif.compatibility

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import codes.laurence.redif.RedifCommandException
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

/** The cases for `HashesAPI`. */
abstract class HashesContract(harness: RedifHarness) : RedifContract(harness) {

  @Test
  fun `hset - answers how many fields were new`() {
    runBlocking {
      val key = randString()

      assertThat(client.hset(key, mapOf("a" to "1", "b" to "2"))).isEqualTo(2)
      assertThat(client.hset(key, mapOf("b" to "20", "c" to "3"))).isEqualTo(1)

      assertThat(client.hgetall(key)).isEqualTo(mapOf("a" to "1", "b" to "20", "c" to "3"))
    }
  }

  @Test
  fun `hset - no fields`() {
    runBlocking {
      val key = randString()

      assertThat(client.hset(key, emptyMap())).isEqualTo(0)

      assertThat(client.exists(key)).isEqualTo(0)
    }
  }

  @Test
  fun `hget - field, missing field and missing key`() {
    runBlocking {
      val key = randString()
      client.hset(key, mapOf("a" to "1"))

      assertThat(client.hget(key, "a")).isEqualTo("1")
      assertThat(client.hget(key, "b")).isNull()
      assertThat(client.hget(randString(), "a")).isNull()
    }
  }

  @Test
  fun `hmget - in field order with null for missing`() {
    runBlocking {
      val key = randString()
      client.hset(key, mapOf("a" to "1", "b" to "2"))

      assertThat(client.hmget(key, "b", "missing", "a", "b")).containsExactly("2", null, "1", "2")
      assertThat(client.hmget(randString(), "a", "b")).containsExactly(null, null)
    }
  }

  @Test
  fun `hgetall - missing key`() {
    runBlocking { assertThat(client.hgetall(randString())).isEmpty() }
  }

  @Test
  fun `hdel - answers how many fields went`() {
    runBlocking {
      val key = randString()
      client.hset(key, mapOf("a" to "1", "b" to "2", "c" to "3"))

      assertThat(client.hdel(key, "a", "b", "missing")).isEqualTo(2)

      assertThat(client.hgetall(key)).isEqualTo(mapOf("c" to "3"))
      assertThat(client.hdel(randString(), "a")).isEqualTo(0)
    }
  }

  @Test
  fun `hdel - the last field removes the key`() {
    runBlocking {
      val key = randString()
      client.hset(key, mapOf("a" to "1"))

      client.hdel(key, "a")

      assertThat(client.exists(key)).isEqualTo(0)
      assertThat(client.type(key)).isEqualTo("none")
    }
  }

  @Test
  fun `hexists and hlen`() {
    runBlocking {
      val key = randString()
      client.hset(key, mapOf("a" to "1", "b" to "2"))

      assertThat(client.hexists(key, "a")).isTrue()
      assertThat(client.hexists(key, "missing")).isFalse()
      assertThat(client.hexists(randString(), "a")).isFalse()
      assertThat(client.hlen(key)).isEqualTo(2)
      assertThat(client.hlen(randString())).isEqualTo(0)
    }
  }

  @Test
  fun `hincrby - a missing key and a missing field count from zero`() {
    runBlocking {
      val key = randString()

      assertThat(client.hincrby(key, "a", 5)).isEqualTo(5)
      assertThat(client.hincrby(key, "a", -7)).isEqualTo(-2)
      assertThat(client.hincrby(key, "b", 1)).isEqualTo(1)

      assertThat(client.hgetall(key)).isEqualTo(mapOf("a" to "-2", "b" to "1"))
    }
  }

  @Test
  fun `hincrby - value is not an integer`() {
    runBlocking {
      val key = randString()
      client.hset(key, mapOf("a" to "one"))

      assertThat(errorCodeOf { client.hincrby(key, "a", 1) }).isEqualTo(RedifCommandException.ERR)
    }
  }

  @Test
  fun `hash commands - key of another type`() {
    runBlocking {
      val key = randString()
      client.set(key, "text")

      val commands: List<suspend () -> Unit> =
          listOf(
              { client.hset(key, mapOf("a" to "1")) },
              { client.hget(key, "a") },
              { client.hmget(key, "a") },
              { client.hgetall(key) },
              { client.hdel(key, "a") },
              { client.hexists(key, "a") },
              { client.hlen(key) },
              { client.hincrby(key, "a", 1) },
          )

      commands.forEach { command ->
        assertThat(errorCodeOf(command)).isEqualTo(RedifCommandException.WRONGTYPE)
      }
      assertThat(client.get(key)).isEqualTo("text")
    }
  }
}

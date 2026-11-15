package codes.laurence.redif

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * What every [RedifAPI] implementation promises. An implementation's test extends this and supplies
 * the client; every case here runs against it, so the in-memory implementation and a client over a
 * real Redis behave alike.
 */
abstract class RedifContract {

  protected abstract val client: RedifAPI

  private fun randString() = "GENERATED${Random.nextInt().absoluteValue}"

  @Test
  fun `set and get`() {
    runBlocking {
      val key = randString()
      val value = randString()

      client.set(key, value)

      assertThat(client.get(key)).isEqualTo(value)
    }
  }

  @Test
  fun `set delete get`() {
    runBlocking {
      val key = randString()
      val value = randString()
      val key2 = randString()
      val value2 = randString()

      client.set(key, value)
      client.set(key2, value2)

      val count = client.del(key, key2)
      assertThat(count).isEqualTo(2)

      assertThat(client.get(key)).isNull()
      assertThat(client.get(key2)).isNull()
    }
  }

  @Test
  fun `psetx and get`() {
    runBlocking {
      val key = randString()
      val value = randString()

      client.psetx(key, value, 1000)

      assertThat(client.get(key)).isEqualTo(value)
    }
  }

  @Test
  fun `psetx and get - expired`() {
    runBlocking {
      val key = randString()
      val value = randString()

      client.psetx(key, value, 10)

      delay(15)

      assertThat(client.get(key)).isNull()
    }
  }

  @Test
  fun `pexpire - success`() {
    runBlocking {
      val key = randString()
      val value = randString()

      client.psetx(key, value, 10)
      assertThat(client.pexpire(key, 20)).isTrue()
      delay(12)

      assertThat(client.get(key)).isEqualTo(value)

      delay(15)

      assertThat(client.get(key)).isNull()
    }
  }

  @Test
  fun `pexpire - does not exist`() {
    runBlocking { assertThat(client.pexpire(randString(), 20)).isFalse() }
  }

  @Test
  fun `setnx - not set yet - get`() {
    runBlocking {
      val key = randString()
      val value = randString()

      val result = client.setnx(key, value)

      assertThat(result).isTrue()

      assertThat(client.get(key)).isEqualTo(value)
    }
  }

  @Test
  fun `setnx - already set - get`() {
    runBlocking {
      val key = randString()
      val value = randString()
      val original = randString()

      client.set(key, original)
      val result = client.setnx(key, value)

      assertThat(result).isFalse()

      assertThat(client.get(key)).isEqualTo(original)
    }
  }

  @Test
  fun `setnx - already set but expired - get`() {
    runBlocking {
      val key = randString()
      val value = randString()
      val original = randString()

      client.set(key, original)
      client.pexpire(key, 10)
      delay(11)
      val result = client.setnx(key, value)

      assertThat(result).isTrue()

      assertThat(client.get(key)).isEqualTo(value)
    }
  }

  @Test
  fun `del with glob - asterisk`() {
    runBlocking {
      client.set("user:1", "one")
      client.set("user:100", "hundred")
      client.set("other:1", "other")

      val count = client.del("user:*")
      assertThat(count).isEqualTo(2)

      assertThat(client.get("user:1")).isNull()
      assertThat(client.get("user:100")).isNull()
      assertThat(client.get("other:1")).isEqualTo("other")
      client.del("other:1")
    }
  }

  @Test
  fun `del with glob - question mark`() {
    runBlocking {
      client.set("hello", "1")
      client.set("hallo", "2")
      client.set("hulloa", "3")

      val count = client.del("h?llo")
      assertThat(count).isEqualTo(2)

      assertThat(client.get("hello")).isNull()
      assertThat(client.get("hallo")).isNull()
      assertThat(client.get("hulloa")).isEqualTo("3")
      client.del("hulloa")
    }
  }

  @Test
  fun `del with glob - brackets`() {
    runBlocking {
      client.set("user:1", "1")
      client.set("user:2", "2")
      client.set("user:3", "3")

      val count = client.del("user:[12]")
      assertThat(count).isEqualTo(2)

      assertThat(client.get("user:1")).isNull()
      assertThat(client.get("user:2")).isNull()
      assertThat(client.get("user:3")).isEqualTo("3")
      client.del("user:3")
    }
  }

  @Test
  fun `del with glob - range`() {
    runBlocking {
      client.set("user:0", "0")
      client.set("user:5", "5")
      client.set("user:9", "9")
      client.set("user:a", "a")

      val count = client.del("user:[0-9]")
      assertThat(count).isEqualTo(3)

      assertThat(client.get("user:0")).isNull()
      assertThat(client.get("user:5")).isNull()
      assertThat(client.get("user:9")).isNull()
      assertThat(client.get("user:a")).isEqualTo("a")
      client.del("user:a")
    }
  }

  @Test
  fun `del with glob - negation`() {
    runBlocking {
      client.set("hallo", "1")
      client.set("hello", "2")

      val count = client.del("h[^e]llo")
      assertThat(count).isEqualTo(1)

      assertThat(client.get("hallo")).isNull()
      assertThat(client.get("hello")).isEqualTo("2")
      client.del("hello")
    }
  }
}

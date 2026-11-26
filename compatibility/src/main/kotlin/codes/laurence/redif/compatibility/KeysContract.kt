package codes.laurence.redif.compatibility

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** The cases for `KeysAPI`. */
abstract class KeysContract(harness: RedifHarness) : RedifContract(harness) {

  @Test
  fun `pexpire - success`() {
    runBlocking {
      val key = randString()
      val value = randString()

      client.psetx(key, value, 10)
      assertThat(client.pexpire(key, 200)).isTrue()
      delay(30)

      assertThat(client.get(key)).isEqualTo(value)

      delay(200)

      assertThat(client.get(key)).isNull()
    }
  }

  @Test
  fun `pexpire - does not exist`() {
    runBlocking { assertThat(client.pexpire(randString(), 20)).isFalse() }
  }

  @Test
  fun `del - keys`() {
    runBlocking {
      val key = randString()
      val key2 = randString()

      client.set(key, randString())
      client.set(key2, randString())

      val count = client.del(key, key2, randString())
      assertThat(count).isEqualTo(2)

      assertThat(client.get(key)).isNull()
      assertThat(client.get(key2)).isNull()
    }
  }

  @Test
  fun `del with glob - asterisk`() {
    runBlocking {
      val prefix = randString()
      client.set("$prefix:user:1", "one")
      client.set("$prefix:user:100", "hundred")
      client.set("$prefix:other:1", "other")

      val count = client.del("$prefix:user:*")
      assertThat(count).isEqualTo(2)

      assertThat(client.get("$prefix:user:1")).isNull()
      assertThat(client.get("$prefix:user:100")).isNull()
      assertThat(client.get("$prefix:other:1")).isEqualTo("other")
    }
  }

  @Test
  fun `del with glob - question mark`() {
    runBlocking {
      val prefix = randString()
      client.set("$prefix:hello", "1")
      client.set("$prefix:hallo", "2")
      client.set("$prefix:hulloa", "3")

      val count = client.del("$prefix:h?llo")
      assertThat(count).isEqualTo(2)

      assertThat(client.get("$prefix:hello")).isNull()
      assertThat(client.get("$prefix:hallo")).isNull()
      assertThat(client.get("$prefix:hulloa")).isEqualTo("3")
    }
  }

  @Test
  fun `del with glob - brackets`() {
    runBlocking {
      val prefix = randString()
      client.set("$prefix:user:1", "1")
      client.set("$prefix:user:2", "2")
      client.set("$prefix:user:3", "3")

      val count = client.del("$prefix:user:[12]")
      assertThat(count).isEqualTo(2)

      assertThat(client.get("$prefix:user:1")).isNull()
      assertThat(client.get("$prefix:user:2")).isNull()
      assertThat(client.get("$prefix:user:3")).isEqualTo("3")
    }
  }

  @Test
  fun `del with glob - range`() {
    runBlocking {
      val prefix = randString()
      client.set("$prefix:user:0", "0")
      client.set("$prefix:user:5", "5")
      client.set("$prefix:user:9", "9")
      client.set("$prefix:user:a", "a")

      val count = client.del("$prefix:user:[0-9]")
      assertThat(count).isEqualTo(3)

      assertThat(client.get("$prefix:user:0")).isNull()
      assertThat(client.get("$prefix:user:5")).isNull()
      assertThat(client.get("$prefix:user:9")).isNull()
      assertThat(client.get("$prefix:user:a")).isEqualTo("a")
    }
  }

  @Test
  fun `del with glob - negation`() {
    runBlocking {
      val prefix = randString()
      client.set("$prefix:hallo", "1")
      client.set("$prefix:hello", "2")

      val count = client.del("$prefix:h[^e]llo")
      assertThat(count).isEqualTo(1)

      assertThat(client.get("$prefix:hallo")).isNull()
      assertThat(client.get("$prefix:hello")).isEqualTo("2")
    }
  }
}

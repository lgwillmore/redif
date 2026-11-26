package codes.laurence.redif.compatibility

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/** The cases for `StringsAPI`. */
abstract class StringsContract(harness: RedifHarness) : RedifContract(harness) {

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
  fun `get - no key`() {
    runBlocking { assertThat(client.get(randString())).isNull() }
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
      delay(15)
      val result = client.setnx(key, value)

      assertThat(result).isTrue()

      assertThat(client.get(key)).isEqualTo(value)
    }
  }
}

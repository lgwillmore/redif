package codes.laurence.redif.compatibility

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import codes.laurence.redif.RedifCommandException
import codes.laurence.redif.commands.keys.ExpireCondition
import codes.laurence.redif.commands.strings.SetArgs
import codes.laurence.redif.commands.strings.SetCondition
import kotlin.test.Test
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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

  @Test
  fun `set - replaces a key of another type and clears its expiry`() {
    runBlocking {
      val key = randString()
      client.hset(key, mapOf("field" to "value"))
      client.pexpire(key, 60_000)

      client.set(key, "text")

      assertThat(client.get(key)).isEqualTo("text")
      assertThat(client.pttl(key)).isEqualTo(-1)
    }
  }

  @Test
  fun `get - key of another type`() {
    runBlocking {
      val key = randString()
      client.hset(key, mapOf("field" to "value"))

      assertThat(errorCodeOf { client.get(key) }).isEqualTo(RedifCommandException.WRONGTYPE)
    }
  }

  @Test
  fun `set with args - no options`() {
    runBlocking {
      val key = randString()

      assertThat(client.set(key, "one", SetArgs())).isEqualTo("OK")

      assertThat(client.get(key)).isEqualTo("one")
    }
  }

  @Test
  fun `set with args - NX`() {
    runBlocking {
      val key = randString()
      val args = SetArgs(condition = SetCondition.NX)

      assertThat(client.set(key, "one", args)).isEqualTo("OK")
      assertThat(client.set(key, "two", args)).isNull()

      assertThat(client.get(key)).isEqualTo("one")
    }
  }

  @Test
  fun `set with args - XX`() {
    runBlocking {
      val key = randString()
      val args = SetArgs(condition = SetCondition.XX)

      assertThat(client.set(key, "one", args)).isNull()
      assertThat(client.get(key)).isNull()

      client.set(key, "one")
      assertThat(client.set(key, "two", args)).isEqualTo("OK")

      assertThat(client.get(key)).isEqualTo("two")
    }
  }

  @Test
  fun `set with args - PX`() {
    runBlocking {
      val key = randString()

      client.set(key, "one", SetArgs(pxMillis = 60_000))

      assertThat(client.pttl(key)).isBetween(50_000, 60_000)
    }
  }

  @Test
  fun `set with args - clears the expiry without KEEPTTL`() {
    runBlocking {
      val key = randString()
      client.psetx(key, "one", 60_000)

      client.set(key, "two", SetArgs())

      assertThat(client.pttl(key)).isEqualTo(-1)
    }
  }

  @Test
  fun `set with args - KEEPTTL`() {
    runBlocking {
      val key = randString()
      client.psetx(key, "one", 60_000)

      client.set(key, "two", SetArgs(keepTtl = true))

      assertThat(client.get(key)).isEqualTo("two")
      assertThat(client.pttl(key)).isBetween(50_000, 60_000)
    }
  }

  @Test
  fun `set with args - GET`() {
    runBlocking {
      val key = randString()
      val args = SetArgs(get = true)

      assertThat(client.set(key, "one", args)).isNull()
      assertThat(client.set(key, "two", args)).isEqualTo("one")

      assertThat(client.get(key)).isEqualTo("two")
    }
  }

  @Test
  fun `set with args - GET with NX answers the value that stopped it`() {
    runBlocking {
      val key = randString()
      val args = SetArgs(condition = SetCondition.NX, get = true)

      assertThat(client.set(key, "one", args)).isNull()
      assertThat(client.set(key, "two", args)).isEqualTo("one")

      assertThat(client.get(key)).isEqualTo("one")
    }
  }

  @Test
  fun `set with args - GET on a key of another type`() {
    runBlocking {
      val key = randString()
      client.sadd(key, "member")

      assertThat(errorCodeOf { client.set(key, "one", SetArgs(get = true)) })
          .isEqualTo(RedifCommandException.WRONGTYPE)
    }
  }

  @Test
  fun `mget - in key order with null for missing and for another type`() {
    runBlocking {
      val one = randString()
      val missing = randString()
      val hash = randString()
      val two = randString()
      client.set(one, "1")
      client.set(two, "2")
      client.hset(hash, mapOf("field" to "value"))

      assertThat(client.mget(one, missing, hash, two, one))
          .containsExactly("1", null, null, "2", "1")
    }
  }

  @Test
  fun `mget - no keys`() {
    runBlocking { assertThat(client.mget()).isEmpty() }
  }

  @Test
  fun `mset - sets every key and clears expiry`() {
    runBlocking {
      val one = randString()
      val two = randString()
      client.psetx(one, "old", 60_000)

      client.mset(mapOf(one to "1", two to "2"))

      assertThat(client.mget(one, two)).containsExactly("1", "2")
      assertThat(client.pttl(one)).isEqualTo(-1)
    }
  }

  @Test
  fun `mset - nothing`() {
    runBlocking { client.mset(emptyMap()) }
  }

  @Test
  fun `getdel - answers the value and deletes the key`() {
    runBlocking {
      val key = randString()
      client.set(key, "one")

      assertThat(client.getdel(key)).isEqualTo("one")

      assertThat(client.get(key)).isNull()
      assertThat(client.getdel(key)).isNull()
    }
  }

  @Test
  fun `getdel - key of another type`() {
    runBlocking {
      val key = randString()
      client.sadd(key, "member")

      assertThat(errorCodeOf { client.getdel(key) }).isEqualTo(RedifCommandException.WRONGTYPE)
      assertThat(client.exists(key)).isEqualTo(1)
    }
  }

  @Test
  fun `incrby - a missing key counts from zero`() {
    runBlocking {
      val key = randString()

      assertThat(client.incrby(key, 5)).isEqualTo(5)
      assertThat(client.incrby(key, 2)).isEqualTo(7)
      assertThat(client.incrby(key, -10)).isEqualTo(-3)

      assertThat(client.get(key)).isEqualTo("-3")
    }
  }

  @Test
  fun `incrby - keeps the expiry`() {
    runBlocking {
      val key = randString()
      client.psetx(key, "1", 60_000)

      client.incrby(key, 1)

      assertThat(client.pttl(key)).isBetween(50_000, 60_000)
    }
  }

  @Test
  fun `incrby - value is not an integer`() {
    runBlocking {
      val key = randString()
      client.set(key, "one")

      assertThat(errorCodeOf { client.incrby(key, 1) }).isEqualTo(RedifCommandException.ERR)
      assertThat(client.get(key)).isEqualTo("one")
    }
  }

  @Test
  fun `incrby - overflow`() {
    runBlocking {
      val key = randString()
      client.set(key, Long.MAX_VALUE.toString())

      assertThat(errorCodeOf { client.incrby(key, 1) }).isEqualTo(RedifCommandException.ERR)
      assertThat(client.get(key)).isEqualTo(Long.MAX_VALUE.toString())
    }
  }

  @Test
  fun `incrby - key of another type`() {
    runBlocking {
      val key = randString()
      client.sadd(key, "member")

      assertThat(errorCodeOf { client.incrby(key, 1) }).isEqualTo(RedifCommandException.WRONGTYPE)
    }
  }

  @Test
  fun `incrby - holds under parallel callers`() {
    runBlocking {
      val key = randString()

      (1..200).map { async { client.incrby(key, 1) } }.awaitAll()

      assertThat(client.get(key)).isEqualTo("200")
    }
  }

  @Test
  fun `incrby then pexpire NX - a fixed window`() {
    runBlocking {
      val key = randString()

      // The first caller of the window gives the count its expiry; later callers leave it alone.
      client.incrby(key, 1)
      assertThat(client.pexpire(key, 60_000, ExpireCondition.NX)).isTrue()
      client.incrby(key, 1)
      assertThat(client.pexpire(key, 600_000, ExpireCondition.NX)).isFalse()

      assertThat(client.get(key)).isEqualTo("2")
      assertThat(client.pttl(key)).isBetween(50_000, 60_000)
    }
  }

  @Test
  fun `decrby - a missing key counts from zero`() {
    runBlocking {
      val key = randString()

      assertThat(client.decrby(key, 5)).isEqualTo(-5)
      assertThat(client.decrby(key, -7)).isEqualTo(2)
    }
  }
}

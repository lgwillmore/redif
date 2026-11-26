package codes.laurence.redif.compatibility

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.containsOnly
import assertk.assertions.isBetween
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import codes.laurence.redif.commands.keys.ExpireCondition
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
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

  @Test
  fun `del with glob - an escaped character matches only itself`() {
    runBlocking {
      val prefix = randString()
      client.set("$prefix:a*c", "1")
      client.set("$prefix:abc", "2")

      val count = client.del("$prefix:a\\*c")
      assertThat(count).isEqualTo(1)

      assertThat(client.get("$prefix:a*c")).isNull()
      assertThat(client.get("$prefix:abc")).isEqualTo("2")
    }
  }

  @Test
  fun `del - every type`() {
    runBlocking {
      val prefix = randString()
      client.set("$prefix:string", "1")
      client.hset("$prefix:hash", mapOf("field" to "value"))
      client.sadd("$prefix:set", "member")
      client.zadd("$prefix:zset", mapOf("member" to 1.0))
      client.pfadd("$prefix:hll", "element")

      assertThat(client.del("$prefix:*")).isEqualTo(5)

      assertThat(client.scan(match = "$prefix:*").toList()).isEmpty()
    }
  }

  @Test
  fun `pexpire - every type`() {
    runBlocking {
      val hash = randString()
      val set = randString()
      val zset = randString()
      client.hset(hash, mapOf("field" to "value"))
      client.sadd(set, "member")
      client.zadd(zset, mapOf("member" to 1.0))

      assertThat(client.pexpire(hash, 10)).isTrue()
      assertThat(client.pexpire(set, 10)).isTrue()
      assertThat(client.pexpire(zset, 10)).isTrue()
      delay(30)

      assertThat(client.exists(hash, set, zset)).isEqualTo(0)
      assertThat(client.hgetall(hash)).isEmpty()
      assertThat(client.scard(set)).isEqualTo(0)
      assertThat(client.zcard(zset)).isEqualTo(0)
    }
  }

  @Test
  fun `pexpire - zero deletes the key`() {
    runBlocking {
      val key = randString()
      client.set(key, "one")

      assertThat(client.pexpire(key, 0)).isTrue()

      assertThat(client.exists(key)).isEqualTo(0)
    }
  }

  @Test
  fun `pexpire with condition - does not exist`() {
    runBlocking {
      ExpireCondition.entries.forEach { condition ->
        assertThat(client.pexpire(randString(), 60_000, condition)).isFalse()
      }
    }
  }

  @Test
  fun `pexpire with condition - NX`() {
    runBlocking {
      val key = randString()
      client.set(key, "one")

      assertThat(client.pexpire(key, 60_000, ExpireCondition.NX)).isTrue()
      assertThat(client.pexpire(key, 600_000, ExpireCondition.NX)).isFalse()

      assertThat(client.pttl(key)).isBetween(50_000, 60_000)
    }
  }

  @Test
  fun `pexpire with condition - XX`() {
    runBlocking {
      val key = randString()
      client.set(key, "one")

      assertThat(client.pexpire(key, 60_000, ExpireCondition.XX)).isFalse()
      assertThat(client.pttl(key)).isEqualTo(-1)

      client.pexpire(key, 60_000)
      assertThat(client.pexpire(key, 600_000, ExpireCondition.XX)).isTrue()

      assertThat(client.pttl(key)).isBetween(590_000, 600_000)
    }
  }

  @Test
  fun `pexpire with condition - GT`() {
    runBlocking {
      val key = randString()
      client.set(key, "one")

      // No expiry is later than any expiry.
      assertThat(client.pexpire(key, 60_000, ExpireCondition.GT)).isFalse()
      assertThat(client.pttl(key)).isEqualTo(-1)

      client.pexpire(key, 60_000)
      assertThat(client.pexpire(key, 30_000, ExpireCondition.GT)).isFalse()
      assertThat(client.pexpire(key, 600_000, ExpireCondition.GT)).isTrue()

      assertThat(client.pttl(key)).isBetween(590_000, 600_000)
    }
  }

  @Test
  fun `pexpire with condition - LT`() {
    runBlocking {
      val key = randString()
      client.set(key, "one")

      // No expiry is later than any expiry.
      assertThat(client.pexpire(key, 600_000, ExpireCondition.LT)).isTrue()
      assertThat(client.pexpire(key, 900_000, ExpireCondition.LT)).isFalse()
      assertThat(client.pexpire(key, 60_000, ExpireCondition.LT)).isTrue()

      assertThat(client.pttl(key)).isBetween(50_000, 60_000)
    }
  }

  @Test
  fun `exists - counts a key once for each time it is named`() {
    runBlocking {
      val one = randString()
      val two = randString()
      client.set(one, "1")
      client.sadd(two, "member")

      assertThat(client.exists(one)).isEqualTo(1)
      assertThat(client.exists(one, two, randString(), one)).isEqualTo(3)
      assertThat(client.exists()).isEqualTo(0)
    }
  }

  @Test
  fun `exists - expired`() {
    runBlocking {
      val key = randString()
      client.psetx(key, "one", 10)
      delay(30)

      assertThat(client.exists(key)).isEqualTo(0)
    }
  }

  @Test
  fun `pttl - no key, no expiry and an expiry`() {
    runBlocking {
      val key = randString()

      assertThat(client.pttl(key)).isEqualTo(-2)

      client.set(key, "one")
      assertThat(client.pttl(key)).isEqualTo(-1)

      client.pexpire(key, 60_000)
      assertThat(client.pttl(key)).isBetween(50_000, 60_000)
    }
  }

  @Test
  fun `persist - removes the expiry`() {
    runBlocking {
      val key = randString()
      client.psetx(key, "one", 60_000)

      assertThat(client.persist(key)).isTrue()

      assertThat(client.pttl(key)).isEqualTo(-1)
      assertThat(client.persist(key)).isFalse()
      assertThat(client.persist(randString())).isFalse()
    }
  }

  @Test
  fun `type - each type`() {
    runBlocking {
      val prefix = randString()
      client.set("$prefix:string", "1")
      client.hset("$prefix:hash", mapOf("field" to "value"))
      client.sadd("$prefix:set", "member")
      client.zadd("$prefix:zset", mapOf("member" to 1.0))
      client.pfadd("$prefix:hll", "element")

      assertThat(client.type("$prefix:string")).isEqualTo("string")
      assertThat(client.type("$prefix:hash")).isEqualTo("hash")
      assertThat(client.type("$prefix:set")).isEqualTo("set")
      assertThat(client.type("$prefix:zset")).isEqualTo("zset")
      // Redis keeps a HyperLogLog in a string.
      assertThat(client.type("$prefix:hll")).isEqualTo("string")
      assertThat(client.type("$prefix:none")).isEqualTo("none")
    }
  }

  @Test
  fun `scan - with match`() {
    runBlocking {
      val prefix = randString()
      val keys = (1..250).map { "$prefix:in:$it" }
      client.mset(keys.associateWith { "value" })
      client.set("$prefix:out", "value")

      // SCAN may name a key twice, so the keys are compared as a set.
      assertThat(client.scan(match = "$prefix:in:*").toList().toSet())
          .containsOnly(*keys.toTypedArray())
      assertThat(client.scan(match = "$prefix:in:*", count = 10).toList().toSet())
          .containsOnly(*keys.toTypedArray())
    }
  }

  @Test
  fun `scan - everything`() {
    runBlocking {
      val one = randString()
      val two = randString()
      client.set(one, "1")
      client.sadd(two, "member")

      val all = client.scan().toList().toSet()

      assertThat(all.intersect(setOf(one, two, randString()))).containsExactlyInAnyOrder(one, two)
    }
  }

  @Test
  fun `scan - leaves out expired keys`() {
    runBlocking {
      val prefix = randString()
      client.psetx("$prefix:gone", "1", 10)
      client.set("$prefix:here", "2")
      delay(30)

      assertThat(client.scan(match = "$prefix:*").toList().toSet()).containsOnly("$prefix:here")
    }
  }
}

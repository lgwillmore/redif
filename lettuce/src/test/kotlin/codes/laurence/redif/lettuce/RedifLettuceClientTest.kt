package codes.laurence.redif.lettuce

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import kotlin.math.absoluteValue
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.measureTime
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** What is particular to the Lettuce client, beyond the contract every implementation passes. */
class RedifLettuceClientTest {

  private fun randString() = "GENERATED${Random.nextLong().absoluteValue}"

  /**
   * Redif has no batch API because it needs none: Lettuce writes a command without waiting for the
   * reply to the one before, so commands from coroutines running together share round trips. Sent
   * one after another, each `set` waits a round trip of its own.
   */
  @Test
  fun `commands from concurrent coroutines are pipelined`() {
    runBlocking {
      RedifLettuce(LettuceHarness.config).use { client ->
        val prefix = randString()
        val commands = 3000
        repeat(500) { client.set("$prefix:warm:$it", "value") }

        val oneAfterAnother = measureTime {
          repeat(commands) { client.set("$prefix:serial:$it", "value") }
        }
        val together = measureTime {
          coroutineScope {
            repeat(commands) { launch { client.set("$prefix:together:$it", "value") } }
          }
        }

        assertThat(client.exists("$prefix:together:0", "$prefix:together:${commands - 1}"))
            .isEqualTo(2)
        assertThat(together).isLessThan(oneAfterAnother / 2)
        client.del("$prefix:*")
      }
    }
  }

  @Test
  fun `close - shuts the client down`() {
    runBlocking {
      val client = RedifLettuce(LettuceHarness.config)
      val key = randString()
      client.set(key, "value")

      client.close()

      val failure = runCatching { client.get(key) }.exceptionOrNull()
      // A closed client is not to be used again; Lettuce refuses the command.
      assertThat(failure).isNotNull()
      LettuceHarness.client().del(key)
    }
  }
}

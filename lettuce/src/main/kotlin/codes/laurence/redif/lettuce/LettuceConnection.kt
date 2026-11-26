package codes.laurence.redif.lettuce

import codes.laurence.redif.RedifClientConfig
import codes.laurence.redif.RedifCommandException
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.RedisCommandExecutionException
import io.lettuce.core.RedisURI
import io.lettuce.core.api.coroutines
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands

/** The Lettuce client and the one connection every command group shares. */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
internal class LettuceConnection(config: RedifClientConfig) {

  private val client: RedisClient
  val commands: RedisCoroutinesCommands<String, String>

  init {
    val uriBuilder = RedisURI.builder()
    uriBuilder.withHost(config.host)
    uriBuilder.withPort(config.port)
    config.auth?.let { uriBuilder.withAuthentication(it.user, it.password) }
    client = RedisClient.create(uriBuilder.build())
    commands = client.connect().coroutines()
  }

  fun close() = client.shutdown()
}

/** Runs Lettuce calls, turning an error reply from Redis into [RedifCommandException]. */
internal inline fun <T> redis(block: () -> T): T =
    try {
      block()
    } catch (e: RedisCommandExecutionException) {
      val message = e.message ?: RedifCommandException.ERR
      throw RedifCommandException(code = message.substringBefore(' '), message = message, cause = e)
    }

/** For a reply Redis always gives, which Lettuce's coroutine API still types as nullable. */
internal fun <T : Any> T?.check(): T =
    this ?: throw IllegalStateException("Lettuce answered null where Redis always replies")

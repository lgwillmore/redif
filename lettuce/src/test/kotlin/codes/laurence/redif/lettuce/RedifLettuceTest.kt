package codes.laurence.redif.lettuce

import codes.laurence.redif.RedifAPI
import codes.laurence.redif.RedifClientConfig
import codes.laurence.redif.RedifContract

/** Runs the contract against the Redis that docker-compose.yml starts. */
class RedifLettuceTest : RedifContract() {
  override val client: RedifAPI =
      RedifLettuce(
          RedifClientConfig(
              host = System.getenv("REDIF_TEST_REDIS_HOST") ?: "localhost",
              port = System.getenv("REDIF_TEST_REDIS_PORT")?.toInt() ?: 6377,
          ))
}

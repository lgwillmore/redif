package codes.laurence.redif.lettuce

import codes.laurence.redif.RedifAPI
import codes.laurence.redif.RedifClientConfig
import codes.laurence.redif.compatibility.HashesContract
import codes.laurence.redif.compatibility.HyperLogLogContract
import codes.laurence.redif.compatibility.KeysContract
import codes.laurence.redif.compatibility.RedifHarness
import codes.laurence.redif.compatibility.SetsContract
import codes.laurence.redif.compatibility.SortedSetsContract
import codes.laurence.redif.compatibility.StringsContract

/** One client for every case, over the Redis that docker-compose.yml starts. */
object LettuceHarness : RedifHarness {
  val config =
      RedifClientConfig(
          host = System.getenv("REDIF_TEST_REDIS_HOST") ?: "localhost",
          port = System.getenv("REDIF_TEST_REDIS_PORT")?.toInt() ?: 6377,
      )

  private val shared = RedifLettuce(config)

  override fun client(): RedifAPI = shared
}

class LettuceHashesTest : HashesContract(LettuceHarness)

class LettuceHyperLogLogTest : HyperLogLogContract(LettuceHarness)

class LettuceKeysTest : KeysContract(LettuceHarness)

class LettuceSetsTest : SetsContract(LettuceHarness)

class LettuceSortedSetsTest : SortedSetsContract(LettuceHarness)

class LettuceStringsTest : StringsContract(LettuceHarness)

package codes.laurence.redif.lettuce

import codes.laurence.redif.RedifAPI
import codes.laurence.redif.RedifClientConfig
import codes.laurence.redif.commands.hashes.HashesAPI
import codes.laurence.redif.commands.hyperloglog.HyperLogLogAPI
import codes.laurence.redif.commands.keys.KeysAPI
import codes.laurence.redif.commands.sets.SetsAPI
import codes.laurence.redif.commands.sortedsets.SortedSetsAPI
import codes.laurence.redif.commands.strings.StringsAPI

/**
 * [RedifAPI] over a Redis instance, 7.0 or later, through Lettuce. Every command goes down one
 * connection, which Lettuce writes to without waiting for earlier replies, so commands sent from
 * coroutines running at the same time are pipelined.
 */
class RedifLettuce private constructor(private val connection: LettuceConnection) :
    RedifAPI,
    StringsAPI by LettuceStrings(connection),
    KeysAPI by LettuceKeys(connection),
    HashesAPI by LettuceHashes(connection),
    SetsAPI by LettuceSets(connection),
    SortedSetsAPI by LettuceSortedSets(connection),
    HyperLogLogAPI by LettuceHyperLogLog(connection) {

  constructor(config: RedifClientConfig) : this(LettuceConnection(config))

  /** Closes the connection and shuts the Lettuce client down. */
  override fun close() = connection.close()
}

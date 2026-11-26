package codes.laurence.redif

import codes.laurence.redif.commands.hashes.HashesAPI
import codes.laurence.redif.commands.hyperloglog.HyperLogLogAPI
import codes.laurence.redif.commands.keys.KeysAPI
import codes.laurence.redif.commands.sets.SetsAPI
import codes.laurence.redif.commands.sortedsets.SortedSetsAPI
import codes.laurence.redif.commands.strings.StringsAPI

/**
 * Every Redis command redif offers, one interface for each Redis command group. A member is the
 * Redis command of the same name, with its argument order and its return meaning.
 *
 * Where Redis refuses a command, the member throws [RedifCommandException] carrying Redis's error
 * code. [close] releases the client's connections; a closed client is not to be used again.
 */
interface RedifAPI :
    StringsAPI, KeysAPI, HashesAPI, SetsAPI, SortedSetsAPI, HyperLogLogAPI, AutoCloseable {
  override fun close()
}

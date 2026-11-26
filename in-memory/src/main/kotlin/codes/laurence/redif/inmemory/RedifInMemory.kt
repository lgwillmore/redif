package codes.laurence.redif.inmemory

import codes.laurence.redif.RedifAPI
import codes.laurence.redif.commands.hashes.HashesAPI
import codes.laurence.redif.commands.hyperloglog.HyperLogLogAPI
import codes.laurence.redif.commands.keys.KeysAPI
import codes.laurence.redif.commands.sets.SetsAPI
import codes.laurence.redif.commands.sortedsets.SortedSetsAPI
import codes.laurence.redif.commands.strings.StringsAPI
import kotlin.time.Clock

/**
 * [RedifAPI] kept in the process. Every command is atomic, as it is in Redis, and keys expire by
 * [clock], which a test can replace.
 */
class RedifInMemory private constructor(keyspace: Keyspace) :
    RedifAPI,
    StringsAPI by InMemoryStrings(keyspace),
    KeysAPI by InMemoryKeys(keyspace),
    HashesAPI by InMemoryHashes(keyspace),
    SetsAPI by InMemorySets(keyspace),
    SortedSetsAPI by InMemorySortedSets(keyspace),
    HyperLogLogAPI by InMemoryHyperLogLog(keyspace) {

  constructor(clock: Clock = Clock.System) : this(Keyspace(clock))

  /** Holds nothing to release. */
  override fun close() {}
}

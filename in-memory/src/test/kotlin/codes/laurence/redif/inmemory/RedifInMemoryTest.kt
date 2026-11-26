package codes.laurence.redif.inmemory

import codes.laurence.redif.RedifAPI
import codes.laurence.redif.compatibility.HashesContract
import codes.laurence.redif.compatibility.HyperLogLogContract
import codes.laurence.redif.compatibility.KeysContract
import codes.laurence.redif.compatibility.RedifHarness
import codes.laurence.redif.compatibility.SetsContract
import codes.laurence.redif.compatibility.SortedSetsContract
import codes.laurence.redif.compatibility.StringsContract

/** One in-memory client for every case, as an application would hold one. */
object InMemoryHarness : RedifHarness {
  private val shared = RedifInMemory()

  override fun client(): RedifAPI = shared
}

class InMemoryHashesTest : HashesContract(InMemoryHarness)

class InMemoryHyperLogLogTest : HyperLogLogContract(InMemoryHarness)

class InMemoryKeysTest : KeysContract(InMemoryHarness)

class InMemorySetsTest : SetsContract(InMemoryHarness)

class InMemorySortedSetsTest : SortedSetsContract(InMemoryHarness)

class InMemoryStringsTest : StringsContract(InMemoryHarness)

package codes.laurence.redif.inmemory

import codes.laurence.redif.RedifAPI
import codes.laurence.redif.compatibility.KeysContract
import codes.laurence.redif.compatibility.RedifHarness
import codes.laurence.redif.compatibility.StringsContract

/** One in-memory client for every case, as an application would hold one. */
object InMemoryHarness : RedifHarness {
  private val shared = RedifInMemory()

  override fun client(): RedifAPI = shared
}

class InMemoryKeysTest : KeysContract(InMemoryHarness)

class InMemoryStringsTest : StringsContract(InMemoryHarness)

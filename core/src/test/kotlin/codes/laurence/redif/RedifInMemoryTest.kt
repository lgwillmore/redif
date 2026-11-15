package codes.laurence.redif

class RedifInMemoryTest : RedifContract() {
  override val client: RedifAPI = RedifInMemory()
}

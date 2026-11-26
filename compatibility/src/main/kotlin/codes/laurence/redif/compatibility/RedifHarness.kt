package codes.laurence.redif.compatibility

import codes.laurence.redif.RedifAPI

/**
 * How an implementation of [RedifAPI] plugs into the contract suites. An implementer writes one
 * harness, and one test class for each suite that extends it and passes the harness in:
 * ```kotlin
 * object MyHarness : RedifHarness {
 *   private val shared = MyRedif(config)
 *
 *   override fun client(): RedifAPI = shared
 * }
 *
 * class MyStringsTest : StringsContract(MyHarness)
 * ```
 *
 * The suites are JUnit 5 classes written with `kotlin.test`, so the implementer's test task must
 * run on the JUnit Platform. Every case makes its own random keys, so a harness may hand every case
 * the same client over the same Redis and need not clear anything between them.
 */
interface RedifHarness {

  /**
   * The client a case runs against. Called once for each case: return a shared client, or a new one
   * and close it in [afterEach].
   */
  fun client(): RedifAPI

  /** Runs before each case, with the client that case will use. */
  suspend fun beforeEach(client: RedifAPI) {}

  /** Runs after each case, passed or failed, with the client that case used. */
  suspend fun afterEach(client: RedifAPI) {}
}
